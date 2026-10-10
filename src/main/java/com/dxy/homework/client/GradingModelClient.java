package com.dxy.homework.client;

import java.util.List;

/**
 * 批改模型客户端抽象。
 * <p>抽成接口是为了让 mock 实现与真实实现可替换，后续接入其它厂商也走同一入口。
 */
public interface GradingModelClient {

    /**
     * @param systemPrompt  固定不变的 system 内容，保持稳定以命中提示词缓存
     * @param userText      本次任务说明
     * @param imageDataUrls 图片的 data URL 列表
     */
    ChatCallResult chatWithImages(String systemPrompt, String userText, List<String> imageDataUrls);
}
