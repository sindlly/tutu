package com.dxy.homework.common;

import lombok.Getter;

/** 业务异常。message 会直接返回给客户端，因此不要在里面带内部细节。 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED);
    }

    public static BizException forbidden() {
        return new BizException(ErrorCode.FORBIDDEN);
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }
}
