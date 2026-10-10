package com.dxy.homework.controller;

import com.dxy.homework.common.R;
import com.dxy.homework.config.DeepSeekProperties;
import com.dxy.homework.config.OcrProperties;
import com.dxy.homework.config.StorageProperties;
import com.dxy.homework.config.WxProperties;
import com.dxy.homework.storage.StorageClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查。顺带把关键外部依赖的配置状态暴露出来，配置漏填时一眼可见。
 * 注意只返回"是否已配置"，绝不返回任何密钥内容。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final StorageClient storageClient;
    private final StorageProperties storageProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final WxProperties wxProperties;
    private final OcrProperties ocrProperties;

    public HealthController(StorageClient storageClient,
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

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("storage", storageClient.name());
        result.put("cosConfigured", storageProperties.cos().configured());
        result.put("localFallbackEnabled", storageProperties.localFallback().enabled());
        result.put("visionModel", deepSeekProperties.model());
        result.put("deepSeekConfigured", deepSeekProperties.hasApiKey());
        result.put("thinkingEnabled", deepSeekProperties.thinkingEnabled());
        result.put("wxConfigured", wxProperties.configured());
        result.put("wxDevLoginEnabled", wxProperties.devLoginEnabled());
        result.put("ocrEnabled", ocrProperties.enabled());
        result.put("ocrConfigured", ocrProperties.configured());
        result.put("ocrAction", ocrProperties.action());
        return R.ok(result);
    }
}
