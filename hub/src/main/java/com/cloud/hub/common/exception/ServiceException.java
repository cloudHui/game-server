package com.cloud.hub.common.exception;

/**
 * 统一业务异常。
 * <p>
 * 当业务校验不通过或发生可预见的业务逻辑中断时抛出，通常由全局异常处理器拦截并转化为
 * 对应的 {@link com.cloud.hub.common.core.domain.AjaxResult#error(int, String)} 返回给客户端。
 * </p>
 *
 * @author cloud
 */
public final class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 错误业务状态码 */
    private Integer code;
    /** 错误简要描述 */
    private String message;
    /** 错误详细补充排查信息 */
    private String detailMessage;

    /**
     * 默认构造函数，状态码默认为 500。
     */
    public ServiceException() {
        this.code = 500;
        this.message = "系统业务异常";
    }

    /**
     * 仅指定错误信息的构造函数，状态码默认为 500。
     *
     * @param message 错误信息
     */
    public ServiceException(String message) {
        super(message);
        this.message = message;
        this.code = 500;
    }

    /**
     * 指定业务错误码与错误信息的构造函数。
     *
     * @param code 业务错误码
     * @param message 错误信息
     */
    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 指定错误信息与业务错误码的重载构造函数。
     *
     * @param message 错误信息
     * @param code 业务错误码
     */
    public ServiceException(String message, Integer code) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码数值
     */
    public Integer getCode() {
        return code != null ? code : 500;
    }

    /**
     * 获取错误提示信息。
     *
     * @return 提示信息
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 获取详细排查信息。
     *
     * @return 详细异常上下文
     */
    public String getDetailMessage() {
        return detailMessage;
    }

    /**
     * 链式设置详细排查信息。
     *
     * @param detailMessage 详细异常上下文
     * @return 当前异常实例
     */
    public ServiceException setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage;
        return this;
    }
}

