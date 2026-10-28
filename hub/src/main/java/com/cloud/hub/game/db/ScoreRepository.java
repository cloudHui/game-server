package com.cloud.hub.game.db;

import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 对局结算积分历史持久化仓储。
 * <p>
 * <b>不可变快照隔离设计：</b>
 * 在对局桌业务线程内迅速深拷贝结算不可变快照 {@link RoundSnapshot}，
 * 随后投递到专用异步阻塞 I/O 线程池进行批量 JDBC Batch 写入，
 * 完全杜绝数据库锁对游戏主循环的任何卡顿影响。
 * </p>
 *
 * @author cloud
 */
public class ScoreRepository {

    private static final Logger logger = LoggerFactory.getLogger(ScoreRepository.class);
    private static volatile ScoreRepository instance;

    /** SQLite JDBC 连接字符串 */
    private final String jdbcUrl;
    /** 异步阻塞落库线程执行器（直接采用标准 Executor） */
    private final java.util.concurrent.Executor asyncIoExecutor;

    public ScoreRepository(String dbPath) {
        this(dbPath, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ScoreRepo-IO");
            t.setDaemon(true);
            return t;
        }));
    }

    public ScoreRepository(String dbPath, java.util.concurrent.Executor asyncIoExecutor) {
        this.asyncIoExecutor = asyncIoExecutor;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("sqlite-jdbc 未找到", e);
        }
        File file = new File(dbPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        initSchema();
    }

    public static void initialize(String dbPath) {
        instance = new ScoreRepository(dbPath);
    }

    public static void initialize(String dbPath, java.util.concurrent.Executor asyncIoExecutor) {
        instance = new ScoreRepository(dbPath, asyncIoExecutor);
    }

    public static ScoreRepository getInstance() {
        ScoreRepository value = instance;
        if (value == null) {
            synchronized (ScoreRepository.class) {
                value = instance;
                if (value == null) instance = value = new ScoreRepository("../lobby/data/lobby.db");
            }
        }
        return value;
    }

    /**
     * 异步持久化指定牌桌的本小局结算战绩。
     *
     * @param table 当前对局桌
     */
    public void saveRound(Table table) {
        RoundSnapshot snapshot = createSnapshot(table);
        if (snapshot == null) return;
        java.util.concurrent.CompletableFuture.runAsync(
                utils.concurrent.thread.DecoratedRunnable.of(() -> writeRound(snapshot), "ScoreRepo-WriteRound", snapshot.tableId),
                asyncIoExecutor
        ).exceptionally(error -> {
            logger.error("保存战绩异步任务失败, tableId={}", snapshot.tableId, error);
            return null;
        });
    }

    /**
     * 在桌子主逻辑线程复制结算数据快照，落库线程只接触不可变数据对象。
     */
    private RoundSnapshot createSnapshot(Table table) {
        if (table.getGameResult().getRoundEntries().isEmpty()) return null;
        GameResult.RoundEntry entry = table.getGameResult().getRoundEntries()
                .get(table.getGameResult().getRoundEntries().size() - 1);
        java.util.List<ScoreRow> rows = new java.util.ArrayList<>();
        for (int seat = 0; seat < table.getTableModel().getSeatNum(); seat++) {
            TableUser user = table.getSeatUser(seat);
            if (user != null) rows.add(new ScoreRow(user.getUserId(), seat, entry.getScores()[seat],
                    table.getGameResult().getTotalScore(seat)));
        }
        return new RoundSnapshot(table.getTableId(), table.getRoomId(), table.getGameType(), entry.getRound(),
                entry.getWinnerSeat(), entry.getScore(), entry.getWinType(), System.currentTimeMillis(), rows);
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000; PRAGMA journal_mode=WAL;");
        }
        return conn;
    }

    /**
     * 分页查询历史对局结算战绩列表。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 战绩记录字典列表
     */
    public java.util.List<java.util.Map<String, Object>> listRecords(int page, int size) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        String sql = "SELECT * FROM score_record ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, Math.min(Math.max(size, 1), 100));
            p.setInt(2, Math.max(0, (page - 1) * size));
            try (java.sql.ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("tableId", r.getLong("table_id"));
                    row.put("roomId", r.getInt("room_id"));
                    row.put("gameType", r.getInt("game_type"));
                    row.put("round", r.getInt("round"));
                    row.put("userId", r.getInt("user_id"));
                    row.put("seat", r.getInt("seat"));
                    row.put("score", r.getInt("score"));
                    row.put("totalScore", r.getInt("total_score"));
                    row.put("createdAt", r.getLong("created_at"));
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            logger.error("listRecords 查询战绩失败", e);
        }
        return rows;
    }

    /**
     * 批量执行 JDBC Batch 写入战绩行。
     */
    private void writeRound(RoundSnapshot snapshot) {
        String sql = "INSERT OR REPLACE INTO score_record(table_id, room_id, game_type, round, user_id, seat, score, total_score, winner_seat, score_value, win_type, created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (ScoreRow row : snapshot.rows) {
                ps.setLong(1, snapshot.tableId);
                ps.setInt(2, snapshot.roomId);
                ps.setInt(3, snapshot.gameType);
                ps.setInt(4, snapshot.round);
                ps.setInt(5, row.userId);
                ps.setInt(6, row.seat);
                ps.setInt(7, row.score);
                ps.setInt(8, row.totalScore);
                ps.setInt(9, snapshot.winnerSeat);
                ps.setInt(10, snapshot.scoreValue);
                ps.setString(11, snapshot.winType);
                ps.setLong(12, snapshot.createdAt);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            logger.error("保存战绩失败, tableId={}, round={}", snapshot.tableId, snapshot.round, e);
        }
    }


    private static final class RoundSnapshot {
        private final long tableId;
        private final int roomId;
        private final int gameType;
        private final int round;
        private final int winnerSeat;
        private final int scoreValue;
        private final String winType;
        private final long createdAt;
        private final java.util.List<ScoreRow> rows;

        private RoundSnapshot(long tableId, int roomId, int gameType, int round, int winnerSeat,
                              int scoreValue, String winType, long createdAt, java.util.List<ScoreRow> rows) {
            this.tableId = tableId;
            this.roomId = roomId;
            this.gameType = gameType;
            this.round = round;
            this.winnerSeat = winnerSeat;
            this.scoreValue = scoreValue;
            this.winType = winType;
            this.createdAt = createdAt;
            this.rows = java.util.Collections.unmodifiableList(rows);
        }
    }

    private static final class ScoreRow {
        private final int userId;
        private final int seat;
        private final int score;
        private final int totalScore;

        private ScoreRow(int userId, int seat, int score, int totalScore) {
            this.userId = userId;
            this.seat = seat;
            this.score = score;
            this.totalScore = totalScore;
        }
    }

    private void initSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS score_record ("
                + "table_id INTEGER NOT NULL, room_id INTEGER NOT NULL, game_type INTEGER NOT NULL,"
                + "round INTEGER NOT NULL, user_id INTEGER NOT NULL, seat INTEGER NOT NULL,"
                + "score INTEGER NOT NULL, total_score INTEGER NOT NULL, winner_seat INTEGER NOT NULL,"
                + "score_value INTEGER NOT NULL, win_type TEXT NOT NULL, created_at INTEGER NOT NULL,"
                + "PRIMARY KEY(table_id, round, user_id))";
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化战绩表失败", e);
        }
    }
}
