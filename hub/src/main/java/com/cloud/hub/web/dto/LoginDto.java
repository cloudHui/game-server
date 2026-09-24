package com.cloud.hub.web.dto;

/**
 * 账号登录请求传输对象。
 *
 * @author cloud
 */
public class LoginDto {

    /** 登录用户名 */
    private String username;

    /** 登录密码 */
    private String password;

    /** 可选的免密续期 Token */
    private String token;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
