package com.dxy.homework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 批改相关参数。 */
@ConfigurationProperties(prefix = "grading")
public record GradingProperties(
        /** 上传给模型前的长边上限。官方会把图缩到约 1300x1300 等效，传更大只是浪费带宽 */
        @DefaultValue("1600") int maxLongSide,
        @DefaultValue("0.85") float jpegQuality,
        /** 低于该置信度视为存疑，UI 应提示用户核对而不是直接判错 */
        @DefaultValue("0.7") double lowConfidence,
        /**
         * 是否生成错题的原图裁剪。
         *
         * <p>默认关闭：实测模型自报的坐标不可靠 —— 同一道题在不同运行里偏差可达 2~3 行，
         * 裁出来常常是相邻的另一道题。展示一张"错误区域的截图"比不展示更糟。
         * 等接入带行级坐标的 OCR、或前端支持手动框选之后再打开。
         */
        @DefaultValue("false") boolean mistakeCropEnabled,
        /**
         * 是否允许用「模型自报坐标」裁剪。默认 false：实测这类坐标偏差可达 2~3 行，
         * 裁出来经常是相邻的另一道题 —— 展示一张错误区域的截图比不展示更糟。
         * 接了 OCR 行坐标之后，裁剪走的是 bbox_source=2 的可靠坐标，不受这项影响。
         */
        @DefaultValue("false") boolean cropAllowAiBbox,
        @DefaultValue Executor executor
) {

    public record Executor(
            @DefaultValue("4") int coreSize,
            @DefaultValue("16") int maxSize,
            @DefaultValue("200") int queueCapacity
    ) {
    }
}
