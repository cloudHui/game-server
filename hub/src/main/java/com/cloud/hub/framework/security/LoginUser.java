package com.cloud.hub.framework.security;

import java.io.Serializable;

/**
 * 登录用户身份凭据实体。
 * <p>
 * 封装已通过身份认证的当前会话用户关键数据（用户 ID、账号名、昵称、Token、SessionId、管理员标志），
 * 存放于请求线程的上下文中供各层业务直接获取。
 * </p>
 *
 * @author cloud
 */
public class LoginUser implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 玩家/管理员全局唯一用户 ID */
    private int userId;
    /** 账号登录名 */
    private String username;
    /** 用户展示昵称 */
    private String nickname;
    /** 访问凭证 Token */
    private String token;
    /** 网页或连接会话 SessionId */
    private String sessionId;
    /** 是否具备系统管理员权限 */
    private boolean admin;

    /**
     * 空构造器。
     */
    public LoginUser() {
    }

    /**
     * 完整字段构造器。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param nickname 昵称
     * @param token 凭证
     * @param sessionId 会话 ID
     * @param admin 是否管理员
     */
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

