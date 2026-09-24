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
 * 全局统一异常拦截与处理中心。
 * <p>
 * 拦截业务异常 {@link ServiceException}、参数校验异常及未知未捕获异常，
 * 统一包装为带有状态码与友好提示的 {@link AjaxResult} 响应，防止敏感堆栈直接暴露给前端。
 * </p>
 *
 * @author cloud
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常拦截处理。
     *
     * @param e 业务异常实例
     * @return 统一错误格式
     */
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e) {
        logger.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    /**
     * JSR-303 JSON 请求体校验失败异常处理。
     *
     * @param e 参数绑定校验异常
     * @return 400 校验错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数验证失败";
        logger.warn("参数校验异常: {}", message);
        return AjaxResult.error(400, message);
    }

    /**
     * 表单/QueryParam 绑定验证失败异常处理。
     *
     * @param e 表单绑定异常
     * @return 400 校验错误响应
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e) {
        String message = e.getAllErrors().isEmpty() ? "参数绑定失败" : e.getAllErrors().get(0).getDefaultMessage();
        logger.warn("参数绑定异常: {}", message);
        return AjaxResult.error(400, message);
    }

    /**
     * 非法参数异常拦截处理。
     *
     * @param e 非法参数异常
     * @return 400 错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数异常: {}", e.getMessage());
        return AjaxResult.error(400, e.getMessage());
    }

    /**
     * 系统未知/未捕获异常统一兜底拦截。
     *
     * @param e 未知异常
     * @return 500 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e) {
        logger.error("系统未处理异常: ", e);
        String msg = (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage() : "系统内部异常，请稍后再试";
        return AjaxResult.error(500, msg);
    }
}

