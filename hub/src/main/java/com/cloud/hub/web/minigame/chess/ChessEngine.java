package com.cloud.hub.web.minigame.chess;

import com.cloud.hub.web.minigame.MiniGameEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * 中国象棋引擎实现。
 * <p>
 * <b>职责边界与使用场景：</b>
 * <ul>
 *   <li>封装 {@link ChessBoard} 的走棋参数解析（fr/fc/tr/tc 四坐标）、红黑方判定（A 执红、B 执黑）、
 *       认输委托、快照与结果构造，向 {@link MiniGameEngine} 契约看齐；</li>
 *   <li>由 {@link com.cloud.hub.web.minigame.MiniRoom} 在象棋房间创建时实例化，
 *       {@link com.cloud.hub.web.handler.MiniGameWebSocketHandler} 通过接口多态调用，
 *       不再直接访问 {@link ChessBoard}。</li>
 * </ul>
 */
public class ChessEngine implements MiniGameEngine {

    private final ChessBoard board = new ChessBoard();

    @Override
    public String gameName() {
        return "chess";
    }

    @Override
    public String sideAName() {
        return "red";
    }

    @Override
    public String sideBName() {
        return "black";
    }

    @Override
    public boolean isFinished() {
        return board.isFinished();
    }

    /**
     * 执行象棋走棋。
     * <p>
     * 从 {@code data} 中解析 {@code fr}、{@code fc}、{@code tr}、{@code tc} 四个坐标，
     * 根据 {@code isSideA} 确定红/黑方（A 方执红），
     * 委托 {@link ChessBoard#move(int, int, int, int, boolean)} 完成合法性校验与行棋。
     */
    @Override
    public MoveResult applyMove(Map<String, Object> data, boolean isSideA) {
        Number fr = (Number) data.get("fr");
        Number fc = (Number) data.get("fc");
        Number tr = (Number) data.get("tr");
        Number tc = (Number) data.get("tc");
        if (fr == null || fc == null || tr == null || tc == null) {
            return MoveResult.fail("缺少走法");
        }
        boolean ok = board.move(fr.intValue(), fc.intValue(), tr.intValue(), tc.intValue(), isSideA);
        if (!ok) {
            return MoveResult.fail("非法走法");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("fr", fr.intValue());
        payload.put("fc", fc.intValue());
        payload.put("tr", tr.intValue());
        payload.put("tc", tc.intValue());
        payload.put("board", board.boardString());
        payload.put("redTurn", board.isRedTurn());
        payload.put("finished", board.isFinished());
        payload.put("winner", board.getWinner());
        payload.put("reason", board.getEndReason());
        return MoveResult.success(payload);
    }

    /**
     * 象棋认输：委托 {@link ChessBoard#resign(boolean)} 标记对局结束并返回结果。
     */
    @Override
    public Map<String, Object> resign(boolean isSideA) {
        // A 方执红，认输则红方认输
        board.resign(isSideA);
        return gameResult();
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("board", board.boardString());
        m.put("redTurn", board.isRedTurn());
        return m;
    }

    @Override
    public Map<String, Object> gameResult() {
        Map<String, Object> m = new HashMap<>();
        m.put("winner", board.getWinner());
        m.put("finished", true);
        m.put("reason", board.getEndReason());
        m.put("board", board.boardString());
        return m;
    }
}
