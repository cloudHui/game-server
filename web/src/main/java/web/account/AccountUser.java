package web.account;

/**
 * 账号行（与 lobby.db user 表对应）
 */
public class AccountUser {
    public long id;
    public String username;
    public String nickname;
    public String passwordHash;
    public boolean enabled;
    public String token;
    public long createdAt;
    public Long lastLoginAt;
}
