package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 邀请码强制废弃请求传输对象。
 *
 * @author cloud
 */
public class InviteRevokeDto {

    /** 目标邀请码 Token */
    @NotBlank(message = "邀请码不能为空")
    private String token;

    /** 会话标识 */
    private String sessionId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
