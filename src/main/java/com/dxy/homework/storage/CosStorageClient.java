package com.dxy.homework.storage;

import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.config.StorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 腾讯云 COS 实现。使用私有桶，读取一律通过预签名 URL，绝不把对象设为公有读。
 */
public class CosStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(CosStorageClient.class);

    private final StorageProperties.Cos props;
    private final COSClient cosClient;
    private final String bucket;

    public CosStorageClient(StorageProperties.Cos props) {
        this.props = props;
        this.bucket = props.resolvedBucket();

        COSCredentials credentials = new BasicCOSCredentials(props.secretId(), props.secretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(props.region()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        clientConfig.setConnectionTimeout((int) props.connectTimeout().toMillis());
        clientConfig.setSocketTimeout((int) props.socketTimeout().toMillis());
        this.cosClient = new COSClient(credentials, clientConfig);

        log.info("COS 存储已启用：region={} bucket={} 预签名有效期={}min",
                props.region(), bucket, props.presignExpire().toMinutes());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);
            // 私有桶：显式指定私有读写，避免桶级默认策略被改动后意外外泄
            metadata.setHeader("x-cos-acl", "private");

            PutObjectRequest request = new PutObjectRequest(
                    bucket, key, new ByteArrayInputStream(content), metadata);
            cosClient.putObject(request);
        } catch (Exception ex) {
            log.error("COS 上传失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public String presignedUrl(String key) {
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucket, key, HttpMethodName.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + props.presignExpire().toMillis()));
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception ex) {
            log.error("COS 生成预签名地址失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public byte[] read(String key) {
        try (COSObject object = cosClient.getObject(bucket, key);
             InputStream in = object.getObjectContent()) {
            return in.readAllBytes();
        } catch (Exception ex) {
            log.error("COS 读取失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        try {
            cosClient.deleteObject(bucket, key);
        } catch (Exception ex) {
            // 删除失败不影响主流程，靠定期清理任务兜底
            log.warn("COS 删除失败 key={}：{}", key, ex.getMessage());
        }
    }

    @Override
    public String name() {
        return "cos:" + bucket;
    }

    @Override
    public void close() {
        cosClient.shutdown();
    }
}
