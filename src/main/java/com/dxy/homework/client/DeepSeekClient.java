package com.dxy.homework.client;

import com.dxy.homework.config.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * DeepSeek OpenAI 兼容接口的最小封装。
 * <p>关键约束（依据官方文档，实现前建议再核对一次）：
 * <ul>
 *   <li>只有 deepseek-flash 支持图像理解，deepseek-v4-pro 传图会直接 400</li>
 *   <li>图片只能出现在 user 消息中，且 content 必须是"内容块数组"而非字符串</li>
 *   <li>单图 ≤ 32MiB，请求体 ≤ 48MiB</li>
 *   <li>每张图片 token 上限 1024，成本可预测</li>
 * </ul>
 */
@Component
public class DeepSeekClient implements GradingModelClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final RestClient restClient;
    private final DeepSeekProperties props;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(RestClient deepSeekRestClient, DeepSeekProperties props, ObjectMapper objectMapper) {
        this.restClient = deepSeekRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatCallResult chatWithImages(String systemPrompt, String userText, List<String> imageDataUrls) {
        if (!props.hasApiKey()) {
            throw new DeepSeekException("缺少 DeepSeek API Key，请先 export DEEPSEEK_API_KEY=sk-xxx", -1, null);
        }

        // 图片只能放在 user 消息里，放 system/assistant 会返回 400
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", buildUserContent(userText, imageDataUrls)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", props.temperature());
        body.put("response_format", Map.of("type", "json_object"));
        // 必须显式传出 disabled。不传该参数会走服务端默认（思考开启），
        // 于是 batched 的思考 token 被计入输出计费，成本与耗时都会失控。
        body.put("thinking", Map.of("type", props.thinkingEnabled() ? "enabled" : "disabled"));
        if (props.thinkingEnabled() && StringUtils.hasText(props.reasoningEffort())) {
            body.put("reasoning_effort", props.reasoningEffort());
        }

        return call(body);
    }

    private List<Map<String, Object>> buildUserContent(String userText, List<String> imageDataUrls) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String dataUrl : imageDataUrls) {
            blocks.add(Map.of(
                    "type", "image_url",
                    // detail 用 original：手写体需要分辨率，而单图 token 有 1024 上限，成本不会因此失控
                    "image_url", Map.of("url", dataUrl, "detail", "original")));
        }
        blocks.add(Map.of("type", "text", "text", userText));
        return blocks;
    }

    private ChatCallResult call(Map<String, Object> body) {
        int maxAttempts = Math.max(1, props.maxRetries() + 1);
        String lastError = null;
        long startedAt = System.currentTimeMillis();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] bytes = restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        // 用 byte[] 接收再按 UTF-8 解码，避免 StringHttpMessageConverter 默认 ISO-8859-1 导致中文乱码
                        .body(byte[].class);
                String raw = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
                return parse(raw, attempt, System.currentTimeMillis() - startedAt);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String respBody = new String(ex.getResponseBodyAsByteArray(), StandardCharsets.UTF_8);
                lastError = "HTTP " + status + " " + abbreviate(respBody);
                // 4xx 多为参数或密钥问题，重试无意义
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt == maxAttempts) {
                    throw new DeepSeekException(lastError, status, ex);
                }
                log.warn("DeepSeek 调用失败（第 {} 次），将重试：{}", attempt, lastError);
            } catch (ResourceAccessException ex) {
                lastError = "网络异常：" + ex.getMessage();
                if (attempt == maxAttempts) {
                    throw new DeepSeekException(lastError, -1, ex);
                }
                log.warn("DeepSeek 网络异常（第 {} 次），将重试：{}", attempt, ex.getMessage());
            }

            sleepQuietly(1000L * attempt);
        }
        throw new DeepSeekException(lastError, -1, null);
    }

    private ChatCallResult parse(String raw, int attempt, long latencyMs) {
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new DeepSeekException("响应不是合法 JSON：" + abbreviate(raw), -1, ex);
        }

        JsonNode message = root.path("choices").path(0).path("message");
        return new ChatCallResult(
                extractText(message.path("content")),
                message.path("reasoning_content").asText(null),
                root.path("id").asText(null),
                root.path("model").asText(props.model()),
                intOrNull(root.path("usage"), "prompt_tokens", "input_tokens"),
                intOrNull(root.path("usage"), "completion_tokens", "output_tokens"),
                cacheHitTokens(root.path("usage")),
                latencyMs,
                attempt,
                raw);
    }

    /** 部分实现会把 content 返回成内容块数组，这里统一合并为纯文本。 */
    private String extractText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : contentNode) {
                String text = block.path("text").asText(null);
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return contentNode.toString();
    }

    private Integer intOrNull(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }

    /** 缓存命中 token 的字段名在不同版本里不一致，逐层兜底。 */
    private Integer cacheHitTokens(JsonNode usage) {
        Integer direct = intOrNull(usage, "prompt_cache_hit_tokens", "cache_hit_tokens", "cached_tokens");
        if (direct != null) {
            return direct;
        }
        return intOrNull(usage.path("prompt_tokens_details"), "cached_tokens");
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ");
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
