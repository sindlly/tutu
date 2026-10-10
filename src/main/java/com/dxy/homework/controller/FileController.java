package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.R;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.service.FileService;
import com.dxy.homework.vo.PresignVo;
import com.dxy.homework.vo.UploadedFileVo;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传作业图片。服务端会先做 EXIF 摆正与压缩，再写入私有桶。
     *
     * @param bizType 1作业原图 2错题裁剪图 3错题卡片 4头像
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<UploadedFileVo> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(defaultValue = "1") Integer bizType) {
        Long userId = AuthContext.requireUserId();
        if (!AppFile.CLIENT_UPLOAD_BIZ_TYPES.contains(bizType)) {
            throw BizException.badRequest("不支持的业务类型");
        }
        return R.ok(fileService.uploadImage(userId, bizType, file));
    }

    /** 获取私有读地址（预签名，带时效）。 */
    @GetMapping("/presign")
    public R<PresignVo> presign(@RequestParam("fileId") Long fileId) {
        Long userId = AuthContext.requireUserId();
        return R.ok(new PresignVo(fileService.presignedUrl(userId, fileId), fileService.presignExpiresInSeconds()));
    }

    /**
     * 本地存储兜底时的回源接口（仅在 storage.local-fallback.enabled=true 时可用）。
     *
     * <p>为什么这里不做登录校验：预签名地址会直接用于 &lt;image src&gt;，浏览器不会带
     * Authorization 头。作为补偿，key 必须已经登记在 t_file 中（随机探测拿不到内容），
     * 且本地实现内部做了目录穿越防护。生产走 COS 时这个接口不可用。
     */
    @GetMapping("/raw")
    public ResponseEntity<byte[]> raw(@RequestParam("key") String key) {
        if (!fileService.localFallbackEnabled()) {
            throw BizException.notFound("接口");
        }
        AppFile file = fileService.findByKey(key);
        if (file == null) {
            throw BizException.notFound("文件");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        file.getMime() == null ? MediaType.IMAGE_JPEG_VALUE : file.getMime()))
                .cacheControl(CacheControl.noStore())
                .body(fileService.readRaw(key));
    }
}
