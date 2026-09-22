package com.cloud.hub.web.minigame;

import java.util.Map;

/**
 * 休闲小游戏统一引擎策略接口。
 * <p>
 * <b>职责边界与使用场景：</b>
 * <ul>
 *   <li>将五子棋、象棋等不同棋类的差异性操作（落子、认输、快照、结果构造、阵营名称）
 *       统一抽象为多态方法，消除 {@link com.cloud.hub.web.handler.MiniGameWebSocketHandler}
 *       中所有基于 {@code gameType} 的条件分支判断；</li>
 *   <li>由 {@link MiniRoom} 在构造时根据游戏类型创建对应引擎实例并持有；</li>
 *   <li>新增棋类只需实现本接口并在 {@link MiniRoom} 构造中注册，无需修改 Handler 逻辑。</li>
 * </ul>
 */
public interface MiniGameEngine {

    /**
     * 游戏标识名称，如 {@code "gomoku"} 或 {@code "chess"}。
     * <p>
     * 用于匹配通知、前端消息的 {@code game} 字段。
     */
    String gameName();

    /**
     * A 方（先手）的阵营显示名称。
     * <p>
     * 五子棋为 {@code "black"}，象棋为 {@code "red"}。
     */
    String sideAName();

    /**
     * B 方（后手）的阵营显示名称。
     * <p>
     * 五子棋为 {@code "white"}，象棋为 {@code "black"}。
     */
    String sideBName();

    /**
     * 对局是否已结束。
     */
    boolean isFinished();

    /**
     * 执行一步操作（落子 / 走棋）。
     * <p>
     * 由引擎自行解析 {@code data} 中的参数（五子棋解析 x/y，象棋解析 fr/fc/tr/tc），
     * 执行合法性校验与棋盘状态推进，返回操作结果。
     *
     * @param data   客户端发送的操作参数
     * @param isSideA 操作方是否为 A 方（先手）
     * @return 操作结果，包含成功标志、错误信息与广播载荷
     */
    MoveResult applyMove(Map<String, Object> data, boolean isSideA);

    /**
     * 处理认输操作。
     * <p>
     * 将对局标记为结束，对手获胜，返回对局结果用于广播。
     *
     * @param isSideA 认输方是否为 A 方
     * @return 对局结果 Map（包含 winner、reason、finished 等字段）
     */
    Map<String, Object> resign(boolean isSideA);

    /**
     * 获取当前棋盘快照，用于匹配成功后向双方发送初始局面。
     *
     * @return 快照数据 Map（五子棋含 board/turn，象棋含 board/redTurn）
     */
    Map<String, Object> snapshot();

    /**
     * 获取对局结果，用于对局结束后的 gameOver 广播。
     *
     * @return 结果 Map（含 winner、finished、reason 等字段）
     */
    Map<String, Object> gameResult();

    /**
     * 操作结果封装。
     * <p>
     * 将落子/走棋的成功与否、错误消息、广播载荷统一封装，
     * 避免 Handler 中散落的参数校验与错误返回逻辑。
     */
    class MoveResult {
        private final boolean ok;
        private final String error;
        private final Map<String, Object> payload;

        private MoveResult(boolean ok, String error, Map<String, Object> payload) {
            this.ok = ok;
            this.error = error;
            this.payload = payload;
        }

        /** 构造成功结果 */
        public static MoveResult success(Map<String, Object> payload) {
            return new MoveResult(true, null, payload);
        }

        /** 构造失败结果 */
        public static MoveResult fail(String error) {
            return new MoveResult(false, error, null);
        }

        public boolean isOk() {
            return ok;
        }

        public String getError() {
            return error;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }
    }
}
