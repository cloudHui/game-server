package com.cloud.hub.lobby.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 注册邀请码数据访问仓储。
 * <p>
 * 封装针对 SQLite {@code invite} 表的增删改查底层 SQL 操作，
 * 支持管理员生成、列表查询、废弃、二次激活以及带 CAS 保护的高并发原子消费。
 * </p>
 *
 * @author cloud
 */
public class InviteRepository {

    private static final Logger logger = LoggerFactory.getLogger(InviteRepository.class);

    /** SQLite 数据库连接驱动单例 */
    private final SqliteDatabase database;

    /**
     * 构造邀请码仓储。
     *
     * @param database SQLite 数据库访问实例
     */
    public InviteRepository(SqliteDatabase database) {
        this.database = database;
    }

    /**
     * 生成并持久化一条新的注册邀请码记录。
     *
     * @param note 备注用途说明
     * @param createdBy 创建者账号名
     * @param expiresAt 到期时间戳（可为 null）
     * @param maxUses 最大允许使用次数
     * @return 创建成功返回邀请码实体，失败返回 null
     */
    public InviteEntity create(String note, String createdBy, Long expiresAt, int maxUses) {
        InviteEntity entity = new InviteEntity();
        entity.setToken(UUID.randomUUID().toString().replace("-", ""));
        entity.setNote(note);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setExpiresAt(expiresAt);
        entity.setMaxUses(maxUses <= 0 ? 1 : maxUses);
        entity.setUsedCount(0);
        entity.setEnabled(true);

        String sql = "INSERT INTO invite(token, note, created_by, created_at, expires_at, max_uses, used_count, enabled)"
                + " VALUES(?,?,?,?,?,?,?,?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entity.getToken());
            ps.setString(2, entity.getNote());
            ps.setString(3, entity.getCreatedBy());
            ps.setLong(4, entity.getCreatedAt());
            if (entity.getExpiresAt() != null) {
                ps.setLong(5, entity.getExpiresAt());
            } else {
                ps.setObject(5, null);
            }
            ps.setInt(6, entity.getMaxUses());
            ps.setInt(7, entity.getUsedCount());
            ps.setInt(8, 1);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entity.setId(keys.getLong(1));
                }
            }
            logger.info("创建邀请码, token={}, maxUses={}", mask(entity.getToken()), entity.getMaxUses());
            return entity;
        } catch (SQLException e) {
            logger.error("创建邀请码失败", e);
            return null;
        }
    }

    /**
     * 按 ID 倒序查询所有邀请码列表。
     *
     * @return 邀请码实体列表
     */
    public List<InviteEntity> listAll() {
        List<InviteEntity> list = new ArrayList<>();
        String sql = "SELECT * FROM invite ORDER BY id DESC";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            logger.error("listAll 邀请码失败", e);
        }
        return list;
    }

    /**
     * 禁用作废指定的邀请码。
     *
     * @param token 邀请码密文
     * @return true 表示成功作废
     */
    public boolean revoke(String token) {
        String sql = "UPDATE invite SET enabled = 0 WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("revoke 失败, token={}", mask(token), e);
            return false;
        }
    }

    /**
     * 重新激活邀请码。
     * <p>
     * 保留历史使用次数，并从当前时间起追加有效期和可用次数。
     * </p>
     *
     * @param token 邀请码密文
     * @param expiresAt 新的截止时间戳
     * @param additionalUses 额外追加的可用次数
     * @return true 表示重新激活成功
     */
    public boolean reactivate(String token, Long expiresAt, int additionalUses) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        int uses = additionalUses <= 0 ? 1 : additionalUses;
        String sql = "UPDATE invite SET enabled = 1, expires_at = ?, max_uses = used_count + ? WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (expiresAt != null) {
                ps.setLong(1, expiresAt);
            } else {
                ps.setObject(1, null);
            }
            ps.setInt(2, uses);
            ps.setString(3, token);
            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logger.info("重新激活邀请码, token={}, additionalUses={}", mask(token), uses);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("reactivate 失败, token={}", mask(token), e);
            return false;
        }
    }

    /**
     * 检验并查看指定邀请码当前是否有效。
     *
     * @param token 待校验邀请码密文
     * @return 若有效返回实体包装，否则返回 empty
     */
    public Optional<InviteEntity> peekValid(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM invite WHERE token = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    InviteEntity entity = map(rs);
                    if (entity.isValidNow()) {
                        return Optional.of(entity);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("peekValid 失败", e);
        }
        return Optional.empty();
    }

    /**
     * 消费邀请码（在单条 SQL 中原子递增 used_count，防止高并发超卖）。
     *
     * @param token 邀请码密文
     * @return true 消费成功
     */
    public boolean consume(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        String sql = "UPDATE invite SET used_count = used_count + 1 WHERE token = ?"
                + " AND enabled = 1"
                + " AND used_count < max_uses"
                + " AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2, now);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                logger.info("消费邀请码成功, token={}", mask(token));
                return true;
            }
            logger.warn("消费邀请码失败(无效或已用尽), token={}", mask(token));
            return false;
        } catch (SQLException e) {
            logger.error("consume 失败", e);
            return false;
        }
    }

    /**
     * 统计系统邀请码总数。
     *
     * @return 记录总数
     */
    public long countInvites() {
        String sql = "SELECT COUNT(1) FROM invite";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("countInvites 失败", e);
        }
        return 0;
    }

    /**
     * 将 JDBC ResultSet 结果集行映射为 InviteEntity 实例。
     */
    private InviteEntity map(ResultSet rs) throws SQLException {
        InviteEntity entity = new InviteEntity();
        entity.setId(rs.getLong("id"));
        entity.setToken(rs.getString("token"));
        entity.setNote(rs.getString("note"));
        entity.setCreatedBy(rs.getString("created_by"));
        entity.setCreatedAt(rs.getLong("created_at"));
        long expires = rs.getLong("expires_at");
        if (!rs.wasNull()) {
            entity.setExpiresAt(expires);
        }
        entity.setMaxUses(rs.getInt("max_uses"));
        entity.setUsedCount(rs.getInt("used_count"));
        entity.setEnabled(rs.getInt("enabled") == 1);
        return entity;
    }

    /**
     * 对邀请码日志打印进行前缀脱敏。
     */
    private static String mask(String token) {
        if (token == null) {
            return "null";
        }
        return token.length() <= 8 ? token : token.substring(0, 8) + "...";
    }
}

