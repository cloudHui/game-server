package com.cloud.hub.web.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import utils.other.MD5Utils;

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
 * Web 侧账号鉴权与用户数据管理服务。
 * <p>
 * 直接读写 {@code lobby.db} 的用户与邀请码数据，提供登录鉴权、注册验证、密码管理等业务能力。
 *
 * @author cloud
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

    /**
     * 构造账号管理服务。
     *
     * @param database 账号数据库管理器
     */
    public AccountService(AccountDatabase database) {
        this.database = database;
    }

    /**
     * 服务启动后检查并初始化默认管理员账号。
     */
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

    /**
     * 升级旧版本遗留的默认弱口令。
     */
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

    /**
     * 是否开放无需邀请码的公开注册。
     *
     * @return true 为开放公开注册
     */
    public boolean isOpenRegister() {
        return openRegister;
    }

    /**
     * 用户名与密码身份鉴权。
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 鉴权成功返回用户对象，失败返回空 Optional
     */
    public Optional<AccountUser> authenticate(String username, String password) {
        Optional<AccountUser> found = findByUsername(username);
        if (!found.isPresent()) {
            logger.warn("[Auth] 登录失败: 用户不存在, username: {}", username);
            recordMetricLogin(false);
            return Optional.empty();
        }
        AccountUser user = found.get();
        if (!user.enabled) {
            logger.warn("[Auth] 登录失败: 账号已被禁用, username: {}, userId: {}", username, user.id);
            recordMetricLogin(false);
            return Optional.empty();
        }
        String hash = MD5Utils.MD5(password);
        if (user.passwordHash == null || !user.passwordHash.equals(hash)) {
            logger.warn("[Auth] 登录失败: 密码错误, username: {}, userId: {}", username, user.id);
            recordMetricLogin(false);
            return Optional.empty();
        }
        String token = newToken();
        long now = System.currentTimeMillis();
        updateLogin(user.id, token, now);
        user.token = token;
        user.lastLoginAt = now;
        logger.info("[Auth] 用户登录成功, username: {}, userId: {}", username, user.id);
        recordMetricLogin(true);
        return Optional.of(user);
    }

    /**
     * 依据 Token 自动免密续期登录。
     *
     * @param token 会话 Token
     * @return 续期成功返回用户对象
     */
    public Optional<AccountUser> authenticateByToken(String token) {
        Optional<AccountUser> found = findByToken(token);
        if (!found.isPresent() || !found.get().enabled) {
            return Optional.empty();
        }
        AccountUser user = found.get();
        String newTok = newToken();
        updateLogin(user.id, newTok, System.currentTimeMillis());
        user.token = newTok;
        logger.info("[Auth] Token 自动续期成功, username: {}, userId: {}", user.username, user.id);
        return Optional.of(user);
    }

    /**
     * 注册新账号。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param nickname 昵称
     * @param invite   邀请码
     * @param outUser  输出参数数组（成功时存放新用户）
     * @return 状态码（CODE_OK 表示成功）
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
        int inviteCheck = checkInvite(invite);
        if (inviteCheck != CODE_OK) {
            return inviteCheck;
        }
        return doRegister(username, password, nickname, invite, outUser);
    }

    /**
     * 校验邀请码合法性。
     */
    private int checkInvite(String invite) {
        if (openRegister) {
            return CODE_OK;
        }
        if (invite.isEmpty()) {
            return CODE_INVITE_REQUIRED;
        }
        return peekInviteValid(invite) ? CODE_OK : CODE_INVITE_INVALID;
    }

    /**
     * 执行注册持久化操作。
     */
    private int doRegister(String username, String password, String nickname, String invite, AccountUser[] outUser) {
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
        if (!openRegister && !consumeInvite(invite)) {
            return CODE_INVITE_INVALID;
        }
        updateLogin(id, entity.token, System.currentTimeMillis());
        entity.id = id;
        if (outUser != null && outUser.length > 0) {
            outUser[0] = entity;
        }
        logger.info("Web 注册成功 userId={} username={}", id, username);
        recordMetricRegister();
        return CODE_OK;
    }

    private static void recordMetricLogin(boolean success) {
        com.cloud.hub.framework.metrics.HubMetrics metrics = com.cloud.hub.framework.metrics.HubMetrics.getInstance();
        if (metrics != null) {
            metrics.recordLogin(success);
        }
    }

    private static void recordMetricRegister() {
        com.cloud.hub.framework.metrics.HubMetrics metrics = com.cloud.hub.framework.metrics.HubMetrics.getInstance();
        if (metrics != null) {
            metrics.recordRegisterSuccess();
        }
    }

    /**
     * 预先检验邀请码是否仍有效（可用次数与过期时间）。
     *
     * @param token 邀请码 Token
     * @return true 为有效
     */
    public boolean peekInviteValid(String token) {
        String sql = "SELECT enabled, expires_at, max_uses, used_count FROM invite WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("enabled") != 1) {
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

    /**
     * 原子核销邀请码可用次数。
     *
     * @param token 邀请码 Token
     * @return true 为成功核销
     */
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

    /**
     * 根据用户名检索账号记录。
     *
     * @param username 用户名
     * @return 账号 Optional
     */
    public Optional<AccountUser> findByUsername(String username) {
        return queryOne("SELECT * FROM user WHERE username = ?", username);
    }

    /**
     * 根据 Token 检索账号记录。
     *
     * @param token 会话 Token
     * @return 账号 Optional
     */
    public Optional<AccountUser> findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return queryOne("SELECT * FROM user WHERE token = ?", token);
    }

    /**
     * 查询全量用户列表。
     *
     * @return 用户列表
     */
    public List<AccountUser> listUsers() {
        List<AccountUser> users = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM user ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs));
            }
        } catch (SQLException e) {
            logger.error("listUsers 失败", e);
        }
        return users;
    }

    /**
     * 管理员后台直接创建受控用户。
     *
     * @param username 用户名
     * @param nickname 昵称
     * @return 创建成功返回新用户对象
     */
    public Optional<AccountUser> createManagedUser(String username, String nickname) {
        username = username == null ? "" : username.trim();
        nickname = nickname == null || nickname.trim().isEmpty() ? username : nickname.trim();
        if (username.isEmpty() || findByUsername(username).isPresent()) {
            return Optional.empty();
        }
        AccountUser user = new AccountUser();
        user.username = username;
        user.nickname = nickname;
        user.passwordHash = MD5Utils.MD5(DEFAULT_USER_PASSWORD);
        user.enabled = true;
        user.createdAt = System.currentTimeMillis();
        user.token = newToken();
        return insert(user) > 0 ? Optional.of(user) : Optional.empty();
    }

    /**
     * 启用或禁用账号。
     *
     * @param username 用户名
     * @param enabled  是否启用
     * @return 是否修改成功
     */
    public boolean setEnabled(String username, boolean enabled) {
        return update("UPDATE user SET enabled = ? WHERE username = ?", enabled ? 1 : 0, username);
    }

    /**
     * 删除指定用户（admin 管理员账号禁止删除）。
     *
     * @param username 用户名
     * @return 是否成功删除
     */
    public boolean deleteUser(String username) {
        if ("admin".equals(username)) {
            return false;
        }
        return update("DELETE FROM user WHERE username = ?", username);
    }

    /**
     * 用户修改自身密码。
     *
     * @param userId      用户 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否修改成功
     */
    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        if (!validPassword(newPassword)) {
            return false;
        }
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

    /**
     * 管理员重置用户密码为默认密码。
     *
     * @param username 用户名
     * @return 是否重置成功
     */
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

    /**
     * 校验密码长度规范（6~64 位）。
     */
    private boolean validPassword(String password) {
        return password != null && password.length() >= 6 && password.length() <= 64;
    }

    /**
     * 通用更新执行模板。
     */
    private boolean update(String sql, Object... values) {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("账号更新失败", e);
            return false;
        }
    }

    /**
     * 统计当前系统用户总数。
     */
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

    /**
     * 插入新用户并回填自增 ID。
     */
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

    /**
     * 更新用户登录会话 Token 与时间戳。
     */
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

    /**
     * 单条记录查询助手。
     */
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

    /**
     * 结果集行映射。
     */
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

    /**
     * 生成安全的随机 UUID 会话 Token。
     */
    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
