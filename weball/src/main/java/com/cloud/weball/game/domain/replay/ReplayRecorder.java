package com.cloud.weball.game.domain.replay;

import java.util.List;
import java.util.Map;

public interface ReplayRecorder {

    // ======================== 头部信息 ========================
    void writeHeader(String gameType, int totalRounds, int seatNum, Map<Integer, Integer> userIds, Map<Integer, String> nicknames);

    void writeConfig(String configLine);

    void writeDealerAndLaiZi(int dealerSeat, int laiZiTileId, int flipTileId);

    // ======================== 初始发牌 ========================
    void writeInitHands(Map<Integer, List<Integer>> hands);

    /** 将当前回放快照落盘，异常中断时仍可读取。 */
    void checkpoint();

    /** 统一审计事件：候选、选择、轮转及大小比较均通过此入口记录。 */
    void writeAuditEvent(String event);

    // ======================== 结算 ========================
    void writeSettlement(int winnerSeat, int fan, String winType, int[] scores);

    // ======================== 保存 ========================
    void save();
}
