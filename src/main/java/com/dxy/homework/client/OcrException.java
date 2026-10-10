package com.dxy.homework.client;

/** OCR 调用失败。定位失败不应该让整次批改失败，调用方要吞掉它并降级。 */
public class OcrException extends RuntimeException {

    private final String code;

    public OcrException(String code, String message) {
        super(message);
        this.code = code;
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    /** 腾讯云返回的错误码，例如 AuthFailure / FailedOperation.UnOpenError。 */
    public String code() {
        return code;
    }
}
