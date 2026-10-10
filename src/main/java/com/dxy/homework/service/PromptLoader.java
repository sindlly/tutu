package com.dxy.homework.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 提示词外置到 resources/prompts/*.st，便于迭代时不用改代码、也不用重启调试。
 */
@Component
public class PromptLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, key -> {
            ClassPathResource resource = new ClassPathResource("prompts/" + key + ".st");
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("提示词文件不存在或读取失败：" + key, ex);
            }
        });
    }
}
