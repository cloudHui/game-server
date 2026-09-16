package com.cloud.hub.common.exception;

/**
 * 业务异常（参照 RuoYi 设计）
 */
public final class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private String detailMessage;

    public ServiceException() {
        this.code = 500;
        this.message = "系统业务异常";
    }

    public ServiceException(String message) {
        super(message);
        this.message = message;
        this.code = 500;
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ServiceException(String message, Integer code) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code != null ? code : 500;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDetailMessage() {
        return detailMessage;
    }

    public ServiceException setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage;
        return this;
    }
}
