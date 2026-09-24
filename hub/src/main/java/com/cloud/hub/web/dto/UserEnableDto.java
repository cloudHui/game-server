package com.cloud.hub.web.dto;

import javax.validation.constraints.NotNull;

/**
 * 用户账号启停状态设置请求传输对象。
 *
 * @author cloud
 */
public class UserEnableDto {

    /** 目标用户自增 ID */
    @NotNull(message = "用户ID不能为空")
    private Integer userId;

    /** 目标启用状态（true: 启用，false: 禁用） */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    /** 会话标识 */
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
