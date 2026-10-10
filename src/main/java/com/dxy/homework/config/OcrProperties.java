package com.dxy.homework.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 腾讯云 OCR 配置，用于给错题在原图上定位（"OCR 选框"）。
 *
 * <p>为什么非得引入 OCR：DeepSeek 官方没有 bounding box 能力，模型自报的坐标实测偏差
 * 可达 2~3 行，裁出来经常是相邻的另一道题。腾讯云通用文字识别（高精度版）会为**每一行文字**
 * 返回 {@code ItemPolygon}（X/Y/Width/Height），这是可靠的像素级坐标 —— 用题干文本去匹配
 * 这些行，就能算出题目的真实区域。
 *
 * <p>密钥默认复用 COS 的同一对子账号密钥（同一个 CAM 账号即可），
 * 也可以通过 OCR_SECRET_ID / OCR_SECRET_KEY 单独指定。
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(
        /** 总开关。关闭后不调用 OCR，错题区域退回模型自报坐标（也就不会生成裁剪图）。 */
        @DefaultValue("true") boolean enabled,
        /** 是否用 OCR 坐标回写题目区域。单独开关，方便只跑 OCR 识别做验证。 */
        @DefaultValue("true") boolean boxEnabled,
        @DefaultValue("") String secretId,
        @DefaultValue("") String secretKey,
        @DefaultValue("ap-guangzhou") String region,
        @DefaultValue("ocr.tencentcloudapi.com") String endpoint,
        /**
         * 接口名。默认 GeneralAccurateOCR（通用文字识别高精度版）—— 官方明确说明它在
         * 手写体、小字、模糊字等困难场景下比通用印刷体识别更准。作业照片正属于这类场景。
         */
        @DefaultValue("GeneralAccurateOCR") String action,
        @DefaultValue("2018-11-19") String version,
        /** 开启原图切图检测，官方说明可提升"整图面积大但单字符占比小"（试卷）的效果 */
        @DefaultValue("true") boolean detectSplit,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        /** 题干与 OCR 行的匹配分低于该值就认为没匹配上，宁可不裁也不裁错 */
        @DefaultValue("0.34") double minMatchScore,
        /** 单次定位最多包含的连续行数，防止把整页框进去 */
        @DefaultValue("8") int maxLinesPerQuestion
) {

    public boolean configured() {
        return enabled && secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
