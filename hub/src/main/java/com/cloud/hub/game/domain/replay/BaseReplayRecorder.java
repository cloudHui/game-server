package com.cloud.hub.game.domain.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 对局过程回放记录器抽象基类。
 * <p>
 * 负责牌桌元数据头部记录、初始手牌记录、审计事件日志流水（带有序号与毫秒级时间戳）、
 * 结算结果记录以及将回放内容原子化落盘（写临时文件再原子移动替换）。
 *
 * @author cloud
 */
public abstract class BaseReplayRecorder implements ReplayRecorder {

    private static final Logger logger = LoggerFactory.getLogger(BaseReplayRecorder.class);

    /** 关联牌桌 ID */
    protected final long tableId;
    /** 当前局数索引 */
    protected final int round;
    /** 回放文本流水缓冲区 */
    protected final StringBuilder sb = new StringBuilder();
    /** 动作序号自增计数器 */
    protected int actionIndex = 0;
    /** 是否已完成结算归档 */
    protected boolean finalized = false;

    /**
     * 构造回放记录器。
     *
     * @param tableId 牌桌 ID
     * @param round   当前对局轮次
     */
    protected BaseReplayRecorder(long tableId, int round) {
        this.tableId = tableId;
        this.round = round;
    }

    @Override
    public void writeHeader(String gameType, int totalRounds, int seatNum, Map<Integer, Integer> userIds, Map<Integer, String> nicknames) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sb.append("=== 回放文件 ===\n");
        sb.append("桌号: ").append(tableId).append("\n");
        sb.append("玩法: ").append(gameType).append("\n");
        sb.append("总局数: ").append(totalRounds).append("\n");
        sb.append("当前局: ").append(round).append("\n");
        sb.append("时间: ").append(sdf.format(new Date())).append("\n");
        sb.append("\n=== 玩家 ===\n");
        for (int i = 0; i < seatNum; i++) {
            sb.append("座").append(i).append(": userId=").append(userIds.getOrDefault(i, 0))
                    .append(", nick=").append(nicknames.getOrDefault(i, "未知")).append("\n");
        }
    }

    @Override
    public void writeConfig(String configLine) {
        sb.append("\n=== 配置 ===\n").append(configLine).append("\n");
    }

    @Override
    public void writeDealerAndLaiZi(int dealerSeat, int laiZiTileId, int flipTileId) {
        // 默认空实现，MJ覆写
    }

    @Override
    public synchronized void writeInitHands(Map<Integer, List<Integer>> hands) {
        sb.append("\n=== 初始发牌 ===\n");
        for (Map.Entry<Integer, List<Integer>> entry : hands.entrySet()) {
            sb.append("座").append(entry.getKey()).append(": ").append(formatList(entry.getValue())).append("\n");
        }
        appendAction("回放状态 已发牌·进行中");
    }

    @Override
    public void writeSettlement(int winnerSeat, int fan, String winType, int[] scores) {
        sb.append("\n=== 结算 ===\n");
        sb.append("番数: ").append(fan).append("\n");
        sb.append("胡牌方式: ").append(winType).append("\n");
        for (int i = 0; i < scores.length; i++) {
            sb.append("座").append(i).append(": ").append(scores[i] >= 0 ? "+" : "").append(scores[i]).append("\n");
        }
    }

    /**
     * 保存回放。每人最多保留 20 条，超出从最旧文件删。
     */
    @Override
    public synchronized void save() {
        if (finalized) return;
        finalized = true;
        sb.append("\n回放状态: 已结算\n");
        persist();
    }

    @Override
    public synchronized void checkpoint() {
        if (!finalized) persist();
    }

    @Override
    public void writeAuditEvent(String event) {
        appendAction(event);
    }

    private void persist() {
        try {
            Path root = ReplayDirectories.root();
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
            String today = dateFmt.format(new Date());
            Path dir = root.resolve(today);
            Files.createDirectories(dir);
            Path file = dir.resolve(tableId + "_" + round + ".txt");
            Path temp = dir.resolve(file.getFileName().toString() + ".tmp");
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.debug("回放快照已保存: {}", file.toAbsolutePath());
            ReplayRetention.prune(root);
        } catch (Exception e) {
            logger.error("保存回放文件失败, tableId: {}, round: {}", tableId, round, e);
        }
    }

    /** 格式化整型列表为逗号分隔字符串，例如 [101, 102] */

    protected String formatList(List<Integer> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(",");
            b.append(list.get(i));
        }
        return b.append("]").toString();
    }

    protected synchronized void appendAction(String action) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        sb.append("[").append(++actionIndex).append("][").append(time).append("] ")
                .append(action).append("\n");
        checkpoint();
    }
}
