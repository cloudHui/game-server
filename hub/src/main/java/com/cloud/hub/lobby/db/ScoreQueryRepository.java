package com.cloud.hub.lobby.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏战绩历史查询仓储。
 * <p>
 * 对应 SQLite 数据库中的 {@code score_record} 表，提供给管理后台或大厅分页查询历史对局结算积分与胜负记录。
 * </p>
 *
 * @author cloud
 */
public class ScoreQueryRepository {

    private static final Logger logger = LoggerFactory.getLogger(ScoreQueryRepository.class);

    /** SQLite 数据库连接驱动单例 */
    private final SqliteDatabase database;

    /**
     * 构造战绩查询仓储。
     *
     * @param database SQLite 数据库访问实例
     */
    public ScoreQueryRepository(SqliteDatabase database) {
        this.database = database;
    }

    /**
     * 分页查询战绩记录。
     *
     * @param offset 偏移量（起始位置）
     * @param limit 查询记录条数上限（单次最大限制 100 条）
     * @return 战绩数据行字典列表
     */
    public List<Map<String, Object>> list(int offset, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT table_id, room_id, game_type, round, user_id, seat, score, total_score, winner_seat, score_value, win_type, created_at"
                + " FROM score_record ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.min(Math.max(limit, 1), 100));
            ps.setInt(2, Math.max(offset, 0));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("查询战绩失败", e);
        }
        return rows;
    }

    /**
     * 将战绩结果集映射为结构化 Map 字典。
     */
    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tableId", rs.getLong("table_id"));
        row.put("roomId", rs.getInt("room_id"));
        row.put("gameType", rs.getInt("game_type"));
        row.put("round", rs.getInt("round"));
        row.put("userId", rs.getInt("user_id"));
        row.put("seat", rs.getInt("seat"));
        row.put("score", rs.getInt("score"));
        row.put("totalScore", rs.getInt("total_score"));
        row.put("winnerSeat", rs.getInt("winner_seat"));
        row.put("scoreValue", rs.getInt("score_value"));
        row.put("winType", rs.getString("win_type"));
        row.put("createdAt", rs.getLong("created_at"));
        return row;
    }
}

