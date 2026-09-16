package com.cloud.hub.framework.security;

import java.io.Serializable;

/**
 * 登录用户身份实体（参照 RuoYi 设计）
 */
public class LoginUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String username;
    private String nickname;
    private String token;
    private String sessionId;
    private boolean admin;

    public LoginUser() {
    }

    public LoginUser(int userId, String username, String nickname, String token, String sessionId, boolean admin) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.token = token;
        this.sessionId = sessionId;
        this.admin = admin;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

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

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
