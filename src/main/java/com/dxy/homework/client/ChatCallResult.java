package com.dxy.homework.client;

/**
 * 一次对话补全调用的结果与用量。
 *
 * @param content          模型输出的正文（已合并多块文本）
 * @param reasoningContent 思考模式下的推理内容，仅用于排查
 * @param requestId        DeepSeek 返回的请求 id，用于与账单对账
 * @param returnedModel    服务端实际提供服务的模型名
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param cacheHitTokens   命中缓存的输入 token（单价约为未命中的 1/50）
 * @param latencyMs        耗时
 * @param attempts         实际尝试次数
 * @param rawJson          原始响应 JSON
 */
public record ChatCallResult(
        String content,
        String reasoningContent,
        String requestId,
        String returnedModel,
        Integer promptTokens,
        Integer completionTokens,
        Integer cacheHitTokens,
        long latencyMs,
        int attempts,
        String rawJson
) {
}
