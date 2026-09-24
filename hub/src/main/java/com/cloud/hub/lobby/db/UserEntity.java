package com.cloud.hub.lobby.db;

/**
 * 用户账号持久化实体。
 * <p>
 * 对应 SQLite 数据库中的 {@code user} 表，维护玩家账户的基本信息、密码散列、启停状态及令牌。
 * </p>
 *
 * @author cloud
 */
public class UserEntity {

    /** 账号唯一自增 ID */
    private long id;
    /** 用户登录名（唯一索引） */
    private String username;
    /** 用户展示昵称 */
    private String nickname;
    /** 加密散列后的密码密文 */
    private String passwordHash;
    /** 账号是否启用（true=正常可用，false=已封禁/禁用） */
    private boolean enabled;
    /** 当前分配给该用户的登录 Token */
    private String token;
    /** 账号注册创建时间戳（毫秒） */
    private long createdAt;
    /** 最近一次成功登录的时间戳（毫秒），未登录过为 null */
    private Long lastLoginAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}

