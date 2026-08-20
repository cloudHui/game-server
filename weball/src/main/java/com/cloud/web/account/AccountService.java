package com.cloud.web.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import utils.other.MD5Utils;
import web.account.AccountDatabase;
import web.account.AccountUser;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Web 侧主鉴权：读写 lobby.db，不依赖 Gate/Lobby 进程。
 */
@Service
public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private static final String DEFAULT_ADMIN_PASSWORD = "admin12345";
    private static final String DEFAULT_USER_PASSWORD = "123456";

    public static final int CODE_OK = 0;
    public static final int CODE_FAIL = 1;
    public static final int CODE_USERNAME_EXISTS = 2;
    public static final int CODE_INVITE_REQUIRED = 3;
    public static final int CODE_INVITE_INVALID = 4;

    private final AccountDatabase database;

    @Value("${account.open-register:false}")
    private boolean openRegister;

    public AccountService(AccountDatabase database) {
        this.database = database;
    }

    @PostConstruct
    public void ensureAdmin() {
        if (countUsers() > 0) {
            upgradeDefaultAdminPassword();
            return;
        }
        AccountUser admin = new AccountUser();
        admin.username = "admin";
        admin.nickname = "管理员";
        admin.passwordHash = MD5Utils.MD5(DEFAULT_ADMIN_PASSWORD);
        admin.enabled = true;
        admin.createdAt = System.currentTimeMillis();
        admin.token = newToken();
        long id = insert(admin);
        if (id > 0) {
            logger.info("已创建默认管理员 admin，已设置独立初始密码，userId={}", id);
        }
    }

    private void upgradeDefaultAdminPassword() {
        String sql = "UPDATE user SET password_hash = ? WHERE username = 'admin' AND password_hash IN (?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MD5Utils.MD5(DEFAULT_ADMIN_PASSWORD));
            ps.setString(2, MD5Utils.MD5(DEFAULT_USER_PASSWORD));
            ps.setString(3, MD5Utils.MD5("admin123"));
            if (ps.executeUpdate() > 0) {
                logger.warn("管理员仍在使用旧默认密码，已升级为新的管理员初始密码");
            }
        } catch (SQLException e) {
            logger.error("升级管理员默认密码失败", e);
        }
    }

    public boolean isOpenRegister() {
        return openRegister;
    }

    public Optional<AccountUser> authenticate(String username, String password) {
        Optional<AccountUser> found = findByUsername(username);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        AccountUser user = found.get();
        if (!user.enabled) {
            return Optional.empty();
        }
        String hash = MD5Utils.MD5(password);
        if (user.passwordHash == null || !user.passwordHash.equals(hash)) {
            return Optional.empty();
        }
        String token = newToken();
        updateLogin(user.id, token, System.currentTimeMillis());
        user.token = token;
        user.lastLoginAt = System.currentTimeMillis();
        return Optional.of(user);
    }

    public Optional<AccountUser> authenticateByToken(String token) {
        Optional<AccountUser> found = findByToken(token);
        if (!found.isPresent() || !found.get().enabled) {
            return Optional.empty();
        }
        AccountUser user = found.get();
        String newTok = newToken();
        updateLogin(user.id, newTok, System.currentTimeMillis());
        user.token = newTok;
        return Optional.of(user);
    }

    /**
     * @return code；成功时 outUser[0] 有值
     */
    public int register(String username, String password, String nickname, String invite, AccountUser[] outUser) {
        username = username == null ? "" : username.trim();
        nickname = nickname == null || nickname.trim().isEmpty() ? username : nickname.trim();
        invite = invite == null ? "" : invite.trim();
        if (username.isEmpty() || password == null || password.isEmpty()) {
            return CODE_FAIL;
        }
        if (findByUsername(username).isPresent()) {
            return CODE_USERNAME_EXISTS;
        }
        boolean needInvite = !openRegister;
        if (needInvite) {
            if (invite.isEmpty()) {
                return CODE_INVITE_REQUIRED;
            }
            if (!peekInviteValid(invite)) {
                return CODE_INVITE_INVALID;
            }
        }
        AccountUser entity = new AccountUser();
        entity.username = username;
        entity.nickname = nickname;
        entity.passwordHash = MD5Utils.MD5(password);
        entity.enabled = true;
        entity.createdAt = System.currentTimeMillis();
        entity.token = newToken();
        long id = insert(entity);
        if (id <= 0) {
            return CODE_FAIL;
        }
        if (needInvite && !consumeInvite(invite)) {
            return CODE_INVITE_INVALID;
        }
        updateLogin(id, entity.token, System.currentTimeMillis());
        entity.id = id;
        if (outUser != null && outUser.length > 0) {
            outUser[0] = entity;
        }
        logger.info("Web 注册成功 userId={} username={}", id, username);
        return CODE_OK;
    }

    public boolean peekInviteValid(String token) {
        String sql = "SELECT enabled, expires_at, max_uses, used_count FROM invite WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                if (rs.getInt("enabled") != 1) {
                    return false;
                }
                long expires = rs.getLong("expires_at");
                if (!rs.wasNull() && expires > 0 && System.currentTimeMillis() > expires) {
                    return false;
                }
                return rs.getInt("used_count") < rs.getInt("max_uses");
            }
        } catch (SQLException e) {
            logger.error("peekInvite 失败", e);
            return false;
        }
    }

    private boolean consumeInvite(String token) {
        long now = System.currentTimeMillis();
        String sql = "UPDATE invite SET used_count = used_count + 1 WHERE token = ?"
                + " AND enabled = 1 AND used_count < max_uses"
                + " AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, now);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("consumeInvite 失败", e);
            return false;
        }
    }

    public Optional<AccountUser> findByUsername(String username) {
        return queryOne("SELECT * FROM user WHERE username = ?", username);
    }

    public Optional<AccountUser> findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return queryOne("SELECT * FROM user WHERE token = ?", token);
    }

    public List<AccountUser> listUsers() {
        List<AccountUser> users = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM user ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(map(rs));
        } catch (SQLException e) {
            logger.error("listUsers 失败", e);
        }
        return users;
    }

    public Optional<AccountUser> createManagedUser(String username, String nickname) {
        username = username == null ? "" : username.trim();
        nickname = nickname == null || nickname.trim().isEmpty() ? username : nickname.trim();
        if (username.isEmpty() || findByUsername(username).isPresent()) return Optional.empty();
        AccountUser user = new AccountUser();
        user.username = username;
        user.nickname = nickname;
        user.passwordHash = MD5Utils.MD5(DEFAULT_USER_PASSWORD);
        user.enabled = true;
        user.createdAt = System.currentTimeMillis();
        user.token = newToken();
        return insert(user) > 0 ? Optional.of(user) : Optional.empty();
    }

    public boolean setEnabled(String username, boolean enabled) {
        return update("UPDATE user SET enabled = ? WHERE username = ?", enabled ? 1 : 0, username);
    }

    public boolean deleteUser(String username) {
        if ("admin".equals(username)) return false;
        return update("DELETE FROM user WHERE username = ?", username);
    }

    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        if (!validPassword(newPassword)) return false;
        String sql = "UPDATE user SET password_hash = ? WHERE id = ? AND password_hash = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MD5Utils.MD5(newPassword));
            ps.setLong(2, userId);
            ps.setString(3, MD5Utils.MD5(oldPassword == null ? "" : oldPassword));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("changePassword 失败 userId={}", userId, e);
            return false;
        }
    }

    public boolean resetPassword(String username) {
        String sql = "UPDATE user SET password_hash = ? WHERE username = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MD5Utils.MD5(DEFAULT_USER_PASSWORD));
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("resetPassword 失败 username={}", username, e);
            return false;
        }
    }

    private boolean validPassword(String password) {
        return password != null && password.length() >= 6 && password.length() <= 64;
    }

    private boolean update(String sql, Object... values) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("账号更新失败", e);
            return false;
        }
    }

    private long countUsers() {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(1) FROM user");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            logger.error("countUsers 失败", e);
            return 0;
        }
    }

    private long insert(AccountUser user) {
        String sql = "INSERT INTO user(username, nickname, password_hash, enabled, token, created_at, last_login_at)"
                + " VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.username);
            ps.setString(2, user.nickname);
            ps.setString(3, user.passwordHash);
            ps.setInt(4, user.enabled ? 1 : 0);
            ps.setString(5, user.token);
            ps.setLong(6, user.createdAt);
            ps.setObject(7, user.lastLoginAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    user.id = id;
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("insert 用户失败 username={}", user.username, e);
        }
        return 0;
    }

    private boolean updateLogin(long userId, String token, long lastLoginAt) {
        String sql = "UPDATE user SET token = ?, last_login_at = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, lastLoginAt);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("updateLogin 失败 userId={}", userId, e);
            return false;
        }
    }

    private Optional<AccountUser> queryOne(String sql, String arg) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("query 失败", e);
        }
        return Optional.empty();
    }

    private static AccountUser map(ResultSet rs) throws SQLException {
        AccountUser u = new AccountUser();
        u.id = rs.getLong("id");
        u.username = rs.getString("username");
        u.nickname = rs.getString("nickname");
        u.passwordHash = rs.getString("password_hash");
        u.enabled = rs.getInt("enabled") == 1;
        u.token = rs.getString("token");
        u.createdAt = rs.getLong("created_at");
        long last = rs.getLong("last_login_at");
        if (!rs.wasNull()) {
            u.lastLoginAt = last;
        }
        return u;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
