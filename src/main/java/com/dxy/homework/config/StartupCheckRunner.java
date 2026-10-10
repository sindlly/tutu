package com.dxy.homework.config;

import com.dxy.homework.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动自检：把容易漏配、且漏配后要过很久才会被发现的问题，在启动日志里一次性讲清楚。
 * 只告警不阻断启动 —— 命令行批改/评测工具在没有对象存储或没有 AppID 的机器上仍要能用。
 */
@Component
@Order(0)
public class StartupCheckRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCheckRunner.class);

    private final StorageClient storageClient;
    private final StorageProperties storageProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final WxProperties wxProperties;
    private final OcrProperties ocrProperties;

    public StartupCheckRunner(StorageClient storageClient,
                              StorageProperties storageProperties,
                              DeepSeekProperties deepSeekProperties,
                              WxProperties wxProperties,
                              OcrProperties ocrProperties) {
        this.storageClient = storageClient;
        this.storageProperties = storageProperties;
        this.deepSeekProperties = deepSeekProperties;
        this.wxProperties = wxProperties;
        this.ocrProperties = ocrProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== 启动自检 ===");
        log.info("对象存储：{}（provider={}）", storageClient.name(), storageProperties.provider());

        if ("unavailable".equals(storageClient.name())) {
            log.error("对象存储不可用，上传与批改链路都会失败。请检查 STORAGE_PROVIDER 与对应凭证");
        }
        if (deepSeekProperties.hasApiKey()) {
            log.info("DeepSeek 模型：{}（思考模式：{}）", deepSeekProperties.model(),
                    deepSeekProperties.thinkingEnabled() ? "开启" : "关闭");
        } else {
            log.error("未配置 DEEPSEEK_API_KEY，批改功能不可用");
        }
        if (wxProperties.configured()) {
            log.info("微信小程序 AppID 已配置");
        } else {
            log.warn("未配置微信 AppID/Secret，只有开发登录可用");
        }

        if (ocrProperties.configured()) {
            log.info("腾讯云 OCR：{}（区域 {}），用于错题选框", ocrProperties.action(), ocrProperties.region());
        } else {
            log.warn("OCR 未配置或已关闭：错题只会用模型自报坐标（不可靠），因此不会生成原图裁剪。"
                    + "需要裁剪请在 .env 配好 OCR_SECRET_ID/OCR_SECRET_KEY（默认复用 COS 密钥）");
        }

        if (wxProperties.devLoginEnabled()) {
            log.warn("!!! 开发登录已开启（WX_DEV_LOGIN_ENABLED=true）：任何请求只要带上 devIdentity "
                    + "就能以任意身份登录。仅限本地开发，上线前必须关闭 !!!");
        }
        log.info("================");
    }
}
