package com.cloud.hub.web.dto;

import javax.validation.constraints.NotNull;

/**
 * 启停用户请求体（对齐 RuoYi 接口入参规范）
 */
public class UserEnableDto {
    @NotNull(message = "用户ID不能为空")
    private Integer userId;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    private String sessionId;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
