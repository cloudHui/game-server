package com.cloud.hub.web.account;

/**
 * 账号实体模型，与 {@code lobby.db} 的 {@code user} 表映射。
 *
 * @author cloud
 */
public class AccountUser {

    /** 自增主键 ID */
    public long id;

    /** 唯一登录用户名 */
    public String username;

    /** 用户昵称 */
    public String nickname;

    /** MD5 密码哈希值 */
    public String passwordHash;

    /** 是否启用账号（true: 正常，false: 禁用） */
    public boolean enabled;

    /** 登录鉴权 Token */
    public String token;

    /** 账号注册时间戳（毫秒） */
    public long createdAt;

    /** 最近一次登录时间戳（毫秒），可能为 null */
    public Long lastLoginAt;
}
