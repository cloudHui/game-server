package com.cloud.hub.framework.web.exception;

import com.cloud.hub.common.core.domain.AjaxResult;
import com.cloud.hub.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常处理器（参照 RuoYi 设计）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常拦截
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e) {
        logger.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 自定义验证异常 (JSR-303 @Valid Body)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数验证失败";
        logger.warn("参数校验异常: {}", message);
        return AjaxResult.error(400, message);
    }

    /**
     * 自定义验证异常 (表单绑定校验)
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e) {
        String message = e.getAllErrors().isEmpty() ? "参数绑定失败" : e.getAllErrors().get(0).getDefaultMessage();
        logger.warn("参数绑定异常: {}", message);
        return AjaxResult.error(400, message);
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数异常: {}", e.getMessage());
        return AjaxResult.error(400, e.getMessage());
    }

    /**
     * 系统未知未知拦截
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e) {
        logger.error("系统未处理异常: ", e);
        return AjaxResult.error(500, e.getMessage() != null && !e.getMessage().isEmpty() ? e.getMessage() : "系统内部异常，请稍后再试");
    }
}
