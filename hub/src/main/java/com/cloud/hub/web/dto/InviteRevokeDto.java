package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 作废邀请码请求体（对齐 RuoYi 接口入参规范）
 */
public class InviteRevokeDto {
    @NotBlank(message = "邀请码不能为空")
    private String token;

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
