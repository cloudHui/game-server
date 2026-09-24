package com.cloud.hub.lobby.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 用户账号数据访问仓储。
 * <p>
 * 封装针对 SQLite {@code user} 表的增删改查底层 SQL 操作，
 * 提供根据 ID、用户名、令牌查询以及用户更新和分页列表等安全接口。
 * </p>
 *
 * @author cloud
 */
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    /** SQLite 数据库连接驱动单例 */
    private final SqliteDatabase database;

    /**
     * 构造用户仓储。
     *
     * @param database SQLite 数据库访问实例
     */
    public UserRepository(SqliteDatabase database) {
        this.database = database;
    }

    /**
     * 统计系统注册用户总数。
     *
     * @return 用户总数
     */
    public long countUsers() {
        String sql = "SELECT COUNT(1) FROM user";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("countUsers 失败", e);
        }
        return 0;
    }

    /**
     * 根据用户主键 ID 检索用户信息。
     *
     * @param id 用户主键
     * @return 匹配的用户实体包装
     */
    public Optional<UserEntity> findById(long id) {
        String sql = "SELECT * FROM user WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findById 失败, id={}", id, e);
        }
        return Optional.empty();
    }

    /**
     * 根据用户名精准检索用户信息。
     *
     * @param username 用户登录名
     * @return 匹配的用户实体包装
     */
    public Optional<UserEntity> findByUsername(String username) {
        String sql = "SELECT * FROM user WHERE username = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByUsername 失败, username={}", username, e);
        }
        return Optional.empty();
    }

    /**
     * 根据客户端持有的会话 Token 查找对应用户。
     *
     * @param token 客户端身份令牌
     * @return 匹配的用户实体包装
     */
    public Optional<UserEntity> findByToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM user WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("findByToken 失败", e);
        }
        return Optional.empty();
    }

    /**
     * 新增注册用户记录。
     *
     * @param user 待插入的用户实体
     * @return 自动生成的用户自增 ID，失败返回 0
     */
    public long insert(UserEntity user) {
        String sql = "INSERT INTO user(username, nickname, password_hash, enabled, token, created_at, last_login_at)"
                + " VALUES(?,?,?,?,?,?,?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getNickname());
            ps.setString(3, user.getPasswordHash());
            ps.setInt(4, user.isEnabled() ? 1 : 0);
            ps.setString(5, user.getToken());
            ps.setLong(6, user.getCreatedAt());
            if (user.getLastLoginAt() != null) {
                ps.setLong(7, user.getLastLoginAt());
            } else {
                ps.setObject(7, null);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    user.setId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("insert 用户失败, username={}", user.getUsername(), e);
        }
        return 0;
    }

    /**
     * 更新用户成功登录后的 Token 与登录时间戳。
     *
     * @param userId 用户 ID
     * @param token 新签发的令牌
     * @param lastLoginAt 登录时间戳
     * @return true 表示更新成功
     */
    public boolean updateLogin(long userId, String token, long lastLoginAt) {
        String sql = "UPDATE user SET token = ?, last_login_at = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, lastLoginAt);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("updateLogin 失败, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 清理用户的 Token（登出或踢下线）。
     *
     * @param userId 用户 ID
     * @return true 表示清理成功
     */
    public boolean clearToken(long userId) {
        String sql = "UPDATE user SET token = NULL WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("clearToken 失败, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 按 ID 倒序分页查询所有用户列表。
     *
     * @param limit 查询记录条数上限
     * @return 用户列表
     */
    public List<UserEntity> listAll(int limit) {
        List<UserEntity> list = new ArrayList<>();
        int lim = limit <= 0 ? 200 : Math.min(limit, 500);
        String sql = "SELECT * FROM user ORDER BY id DESC LIMIT ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lim);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("listAll 失败", e);
        }
        return list;
    }

    /**
     * 启用或封禁禁用指定用户账号。
     *
     * @param userId 用户 ID
     * @param enabled 是否启用
     * @return true 表示修改成功
     */
    public boolean setEnabled(long userId, boolean enabled) {
        String sql = "UPDATE user SET enabled = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("setEnabled 失败, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 将 JDBC ResultSet 结果集行映射为 UserEntity 实例。
     *
     * @param rs 数据库游标
     * @return 用户实体
     * @throws SQLException 提取字段时的 SQL 异常
     */
    private UserEntity map(ResultSet rs) throws SQLException {
        UserEntity entity = new UserEntity();
        entity.setId(rs.getLong("id"));
        entity.setUsername(rs.getString("username"));
        entity.setNickname(rs.getString("nickname"));
        entity.setPasswordHash(rs.getString("password_hash"));
        entity.setEnabled(rs.getInt("enabled") == 1);
        entity.setToken(rs.getString("token"));
        entity.setCreatedAt(rs.getLong("created_at"));
        long last = rs.getLong("last_login_at");
        if (!rs.wasNull()) {
            entity.setLastLoginAt(last);
        }
        return entity;
    }
}

