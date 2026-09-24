package com.cloud.hub.lobby.db;

import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义房间模板数据访问仓储。
 * <p>
 * 对应 SQLite 数据库中的 {@code custom_room} 表，负责管理后台或玩家自定义的对局房间规则模板（JSON），
 * 支持持久化保存、启用模板加载以及全量失效清理脏数据。
 * </p>
 *
 * @author cloud
 */
public class CustomRoomRepository {

    private static final Logger logger = LoggerFactory.getLogger(CustomRoomRepository.class);

    /** SQLite 数据库连接驱动单例 */
    private final SqliteDatabase database;

    /**
     * 构造自定义房间模板仓储。
     *
     * @param database SQLite 数据库访问实例
     */
    public CustomRoomRepository(SqliteDatabase database) {
        this.database = database;
    }

    /**
     * 持久化或更新自定义房间模板。
     *
     * @param model 房间规则配置模型（要求 ID >= 10000 属于自定义段）
     * @param createdBy 创建者账号名
     * @return true 表示保存成功
     */
    public boolean save(TableModel model, String createdBy) {
        if (model == null || model.getId() < 10000) {
            return false;
        }
        String sql = "INSERT OR REPLACE INTO custom_room(model_id, model_json, game_type, created_by, created_at, enabled)"
                + " VALUES(?,?,?,?,?,1)";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, model.getId());
            ps.setString(2, TableModelJson.toJson(model));
            ps.setInt(3, model.getType());
            ps.setString(4, createdBy);
            ps.setLong(5, System.currentTimeMillis());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.error("保存自定义房间模板失败, modelId={}", model.getId(), e);
            return false;
        }
    }

    /**
     * 加载当前所有已启用的自定义房间模板。
     *
     * @return 启用的模板对象列表
     */
    public List<TableModel> listEnabled() {
        List<TableModel> models = new ArrayList<>();
        String sql = "SELECT model_json FROM custom_room WHERE enabled = 1 ORDER BY model_id";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                TableModel model = TableModelJson.parse(rs.getString(1));
                if (model != null && model.getId() >= 10000) {
                    models.add(model);
                }
            }
        } catch (SQLException e) {
            logger.error("加载自定义房间模板失败", e);
        }
        return models;
    }

    /**
     * 禁用全部历史自定义模板，清理大厅脏数据。
     *
     * @return 被禁用的模板数量
     */
    public int disableAll() {
        String sql = "UPDATE custom_room SET enabled = 0 WHERE enabled = 1";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int n = ps.executeUpdate();
            if (n > 0) {
                logger.info("已禁用历史自定义房间模板, count={}", n);
            }
            return n;
        } catch (SQLException e) {
            logger.error("禁用自定义房间模板失败", e);
            return 0;
        }
    }
}

