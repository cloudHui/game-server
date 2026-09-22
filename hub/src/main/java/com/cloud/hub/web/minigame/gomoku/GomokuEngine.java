package com.cloud.hub.web.minigame.gomoku;

import java.util.HashMap;
import java.util.Map;

import com.cloud.hub.web.minigame.MiniGameEngine;

/**
 * 五子棋引擎实现。
 * <p>
 * <b>职责边界与使用场景：</b>
 * <ul>
 *   <li>封装 {@link GomokuBoard} 的落子参数解析（x/y 坐标）、颜色计算（A 执黑、B 执白）、
 *       认输、快照与结果构造，向 {@link MiniGameEngine} 契约看齐；</li>
 *   <li>由 {@link com.cloud.hub.web.minigame.MiniRoom} 在五子棋房间创建时实例化，
 *       {@link com.cloud.hub.web.handler.MiniGameWebSocketHandler} 通过接口多态调用，
 *       不再直接访问 {@link GomokuBoard}。</li>
 * </ul>
 */
public class GomokuEngine implements MiniGameEngine {

    /** 底层五子棋盘核心逻辑状态实例（包含 15x15 棋盘数组、行棋方及连珠判定） */
    private final GomokuBoard board = new GomokuBoard();

    /**
     * 获取小游戏唯一英文标识。
     *
     * @return 游戏类型标识 "gomoku"
     */
    @Override
    public String gameName() {
        return "gomoku";
    }

    /**
     * 获取 A 方阵营名称。
     *
     * @return A 方对应执黑 "black"（先行方）
     */
    @Override
    public String sideAName() {
        return "black";
    }

    /**
     * 获取 B 方阵营名称。
     *
     * @return B 方对应执白 "white"（后手方）
     */
    @Override
    public String sideBName() {
        return "white";
    }

    /**
     * 判定五子棋当前对局是否已结束（分出胜负或棋盘已满和棋）。
     *
     * @return true 表示已终局
     */
    @Override
    public boolean isFinished() {
        return board.isFinished();
    }

    /**
     * 执行五子棋落子。
     * <p>
     * 从 {@code data} 中解析 {@code x}、{@code y} 坐标，根据 {@code isSideA} 确定棋子颜色
     * （A 方执黑先行），委托 {@link GomokuBoard#place(int, int, int)} 完成合法性校验与落子。
     *
     * @param data    落子参数字典（包含坐标 x, y）
     * @param isSideA 是否为 A 方（执黑）
     * @return 落子执行结果（成功包含棋盘快照与行棋方，失败包含错误原因）
     */
    @Override
    public MoveResult applyMove(Map<String, Object> data, boolean isSideA) {
        Number xNum = (Number) data.get("x");
        Number yNum = (Number) data.get("y");
        if (xNum == null || yNum == null) {
            return MoveResult.fail("缺少坐标");
        }
        int color = isSideA ? GomokuBoard.BLACK : GomokuBoard.WHITE;
        boolean ok = board.place(xNum.intValue(), yNum.intValue(), color);
        if (!ok) {
            return MoveResult.fail("非法落子");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("x", xNum.intValue());
        payload.put("y", yNum.intValue());
        payload.put("color", color);
        payload.put("turn", board.getTurn());
        payload.put("finished", board.isFinished());
        payload.put("winner", board.getWinner());
        return MoveResult.success(payload);
    }

    /**
     * 五子棋认输：对手获胜。
     * <p>
     * 五子棋无内置 resign 方法，直接构造结果 Map 标记对手为赢家。
     *
     * @param isSideA 是否为 A 方认输
     * @return 认输终局结果字典（包含赢家 winner、原因 reason 等）
     */
    @Override
    public Map<String, Object> resign(boolean isSideA) {
        // 认输方的对手获胜
        int winner = isSideA ? GomokuBoard.WHITE : GomokuBoard.BLACK;
        Map<String, Object> result = new HashMap<>();
        result.put("winner", winner);
        result.put("reason", "认输");
        result.put("finished", true);
        return result;
    }

    /**
     * 生成当前五子棋盘完整快照字典。
     *
     * @return 包含 15x15 棋盘矩阵以及当前行棋方 turn 的快照
     */
    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("board", board.snapshot());
        m.put("turn", board.getTurn());
        return m;
    }

    /**
     * 构造并返回五子棋终局结算信息。
     *
     * @return 包含赢家 winner（0和棋、1黑胜、2白胜）、原因 reason 与终局标记
     */
    @Override
    public Map<String, Object> gameResult() {
        Map<String, Object> m = new HashMap<>();
        m.put("winner", board.getWinner());
        m.put("finished", true);
        m.put("reason", board.getWinner() == 0 ? "和棋" : "五子连珠");
        return m;
    }
}

