package com.dxy.homework.common;

import lombok.Getter;

/** 业务错误码。code 为 0 表示成功，其余按 HTTP 语义设计。 */
@Getter
public enum ErrorCode {

    OK(0, "ok"),
    BAD_REQUEST(400, "参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "重复的请求"),
    QUOTA_EXCEEDED(429, "今日批改次数已用完"),
    TOO_MANY_REQUESTS(4291, "操作太频繁，请稍后再试"),
    PAYLOAD_TOO_LARGE(413, "文件过大"),
    INTERNAL_ERROR(500, "服务异常，请稍后再试"),
    /** AI 服务不可用，此时应返还额度 */
    AI_UNAVAILABLE(5001, "批改服务暂时不可用，请稍后重试"),
    STORAGE_ERROR(5002, "文件服务暂时不可用，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
