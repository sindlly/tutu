package com.dxy.homework.model;

/**
 * 预处理后的图片：已按 EXIF 摆正、已压缩到可控尺寸。
 *
 * <p>同时保留编码后的字节与 base64 两种形态 —— 上传到对象存储用字节，
 * 送给模型用 base64，避免同一张图被编码两次。
 */
public record PreparedImage(
        String fileName,
        String mimeType,
        int width,
        int height,
        long originalBytes,
        byte[] content,
        String base64
) {

    public String dataUrl() {
        return "data:" + mimeType + ";base64," + base64;
    }

    public long encodedBytes() {
        return content == null ? 0 : content.length;
    }
}
