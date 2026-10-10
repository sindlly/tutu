package com.dxy.homework.model;

/**
 * OCR 识别出的一行文字及其在原图中的位置。
 *
 * <p>坐标是**归一化**的（0-1，相对送给 OCR 的那张图），因为裁剪时用的是同一张预处理图，
 * 归一化后就不必再关心图片被缩放到多少像素。
 */
public record OcrLine(
        String text,
        double x,
        double y,
        double w,
        double h,
        Double confidence,
        /** 所在页序号，从 1 开始 */
        int pageSeq
) {

    public double bottom() {
        return y + h;
    }

    public double right() {
        return x + w;
    }

    public double centerY() {
        return y + h / 2.0;
    }
}
