package com.dxy.homework.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

/** 统一异常出口。对外不暴露堆栈与内部细节，完整信息只进日志。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException ex, HttpServletRequest request) {
        log.info("业务异常 [{} {}] {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getErrorCode().name(), ex.getMessage());
        return R.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidation(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), message.isEmpty() ? "参数不合法" : message);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public R<Void> handleBadRequest(Exception ex) {
        log.info("请求参数异常：{}", ex.getMessage());
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), "参数不合法");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleTooLarge(MaxUploadSizeExceededException ex) {
        log.info("上传文件超过限制：{}", ex.getMessage());
        return R.fail(ErrorCode.PAYLOAD_TOO_LARGE.getCode(), "文件过大，请压缩后重试");
    }

    @ExceptionHandler({NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    public R<Void> handleNotFound(Exception ex) {
        log.info("接口不存在或方法不支持：{}", ex.getMessage());
        return R.fail(ErrorCode.NOT_FOUND.getCode(), "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期异常 [{} {}]", request.getMethod(), request.getRequestURI(), ex);
        return R.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
