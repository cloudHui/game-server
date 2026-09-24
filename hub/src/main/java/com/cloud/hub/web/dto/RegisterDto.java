package com.cloud.hub.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 账号注册请求传输对象。
 *
 * @author cloud
 */
public class RegisterDto {

    /** 注册用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 注册初始密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 可选的用户昵称 */
    private String nickname;

    /** 注册邀请码（未开放公开注册时必填） */
    @NotBlank(message = "邀请码不能为空")
    private String invite;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getInvite() {
        return invite;
    }

    public void setInvite(String invite) {
        this.invite = invite;
    }
}
