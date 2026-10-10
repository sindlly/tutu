package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.config.StorageProperties;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.mapper.AppFileMapper;
import com.dxy.homework.model.PreparedImage;
import com.dxy.homework.storage.StorageClient;
import com.dxy.homework.vo.UploadedFileVo;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务：预处理 → 上传对象存储 → 落元数据。
 *
 * <p>所有对外读取都必须经过 {@link #requireOwned} 做归属校验，避免用户拿到别人的作业照片。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private final AppFileMapper fileMapper;
    private final StorageClient storageClient;
    private final ImagePreparer imagePreparer;
    private final StorageProperties storageProperties;

    public FileService(AppFileMapper fileMapper,
                       StorageClient storageClient,
                       ImagePreparer imagePreparer,
                       StorageProperties storageProperties) {
        this.fileMapper = fileMapper;
        this.storageClient = storageClient;
        this.imagePreparer = imagePreparer;
        this.storageProperties = storageProperties;
    }

    /** 上传作业图片：先做 EXIF 摆正与压缩，再入库。 */
    public UploadedFileVo uploadImage(Long userId, int bizType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的图片");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE, "图片超过 20MB，请压缩后重试");
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException ex) {
            throw BizException.badRequest("读取上传内容失败");
        }

        PreparedImage prepared = imagePreparer.prepare(raw, safeName(file.getOriginalFilename()));
        return store(userId, bizType, prepared);
    }

    /** 保存已预处理好的图片（上传链路用）。 */
    public UploadedFileVo store(Long userId, int bizType, PreparedImage prepared) {
        Stored result = upsert(userId, bizType, prepared.content(), prepared.mimeType(),
                prepared.width(), prepared.height());
        return UploadedFileVo.of(result.file(), result.reused());
    }

    /** 保存服务端生成的图片（错题卡片、原图裁剪）时用，不需要额外的预处理信息。 */
    public AppFile storeBytes(Long userId, int bizType, byte[] content, String mimeType) {
        return upsert(userId, bizType, content, mimeType, null, null).file();
    }

    public record Stored(AppFile file, boolean reused) {
    }

    /**
     * 按内容指纹去重后落库。
     *
     * <p>前提是"数据库行"与"对象"始终同生同灭：上传顺序是先 put 再 insert，put 失败就不会有孤儿行；
     * 删除时必须两者一起删。切换存储实现（minio ↔ cos）会导致历史行的对象不存在，
     * 那属于数据迁移问题，需要单独做一次对象搬迁。
     */
    private Stored upsert(Long userId, int bizType, byte[] content, String mimeType,
                          Integer width, Integer height) {
        String sha256 = sha256Hex(content);

        AppFile existing = fileMapper.selectOne(Wrappers.<AppFile>lambdaQuery()
                .eq(AppFile::getUserId, userId)
                .eq(AppFile::getBizType, bizType)
                .eq(AppFile::getSha256, sha256)
                .eq(AppFile::getStatus, 1)
                .last("LIMIT 1"));
        if (existing != null) {
            return new Stored(existing, true);
        }

        String key = buildKey(bizType, mimeType);
        storageClient.put(key, content, mimeType);

        AppFile entity = new AppFile();
        entity.setUserId(userId);
        entity.setBizType(bizType);
        entity.setFileKey(key);
        entity.setMime(mimeType);
        entity.setSize(content.length);
        entity.setWidth(width);
        entity.setHeight(height);
        entity.setSha256(sha256);
        entity.setStatus(1);
        fileMapper.insert(entity);

        log.debug("文件已入库 fileId={} key={} {}KB", entity.getId(), key, content.length / 1024);
        return new Stored(entity, false);
    }

    /** 取文件并校验归属。任何按 fileId 的读操作都必须先过这一关。 */
    public AppFile requireOwned(Long userId, Long fileId) {
        AppFile file = fileMapper.selectById(fileId);
        if (file == null || !Integer.valueOf(1).equals(file.getStatus())) {
            throw BizException.notFound("文件");
        }
        if (!file.getUserId().equals(userId)) {
            // 不区分"不存在"和"无权限"之外的细节，避免通过报错探测他人 fileId
            throw BizException.forbidden();
        }
        return file;
    }

    public String presignedUrl(Long userId, Long fileId) {
        AppFile file = requireOwned(userId, fileId);
        return storageClient.presignedUrl(file.getFileKey());
    }

    public long presignExpiresInSeconds() {
        return storageProperties.cos().presignExpire().toSeconds();
    }

    public boolean localFallbackEnabled() {
        return storageProperties.localFallback().enabled();
    }

    public byte[] readRaw(String key) {
        return storageClient.read(key);
    }

    /** 按 key 反查文件（仅供本地存储兜底的回源接口使用）。 */
    public AppFile findByKey(String key) {
        return fileMapper.selectOne(Wrappers.<AppFile>lambdaQuery()
                .eq(AppFile::getFileKey, key)
                .eq(AppFile::getStatus, 1)
                .last("LIMIT 1"));
    }

    private String buildKey(int bizType, String mimeType) {
        String ext = "image/png".equals(mimeType) ? "png" : "jpg";
        LocalDate today = LocalDate.now(ZONE);
        return "%s/%d/%02d/%02d/%s.%s".formatted(
                prefix(bizType), today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID().toString().replace("-", ""), ext);
    }

    private String prefix(int bizType) {
        return switch (bizType) {
            case AppFile.BIZ_MISTAKE_CROP -> "mistake-crop";
            case AppFile.BIZ_MISTAKE_CARD -> "mistake-card";
            case AppFile.BIZ_AVATAR -> "avatar";
            default -> "homework";
        };
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM 不支持 SHA-256", ex);
        }
    }

    /** 只取白名单扩展名，避免原始文件名污染对象 key。 */
    private String safeName(String originalFilename) {
        if (originalFilename == null) {
            return "image";
        }
        String name = originalFilename.replaceAll("[\\\\/]", "");
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.isBlank() ? "image" : name;
    }
}
