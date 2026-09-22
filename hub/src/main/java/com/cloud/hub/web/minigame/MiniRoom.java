package com.cloud.hub.web.minigame;

import com.cloud.hub.web.minigame.chess.ChessEngine;
import com.cloud.hub.web.minigame.gomoku.GomokuEngine;
import java.util.UUID;

/**
 * 休闲小游戏房间（1v1 对局）。
 * <p>
 * <b>职责边界与使用场景：</b>
 * <ul>
 *   <li>管理房间内双方玩家的会话、ID 与昵称映射；</li>
 *   <li>持有 {@link MiniGameEngine} 引擎实例，将具体棋类的差异性操作委托给引擎多态处理；</li>
 *   <li>由 {@link com.cloud.hub.web.handler.MiniGameWebSocketHandler} 在匹配成功时创建。</li>
 * </ul>
 */
public class MiniRoom {
    public enum GameType {
        GOMOKU, CHESS
    }

    private final String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final GameType gameType;
    private final String playerASession;
    private final int playerAUserId;
    private final String playerAName;
    private final String playerBSession;
    private final int playerBUserId;
    private final String playerBName;

    /** 统一引擎实例，封装具体棋类的全部差异化操作 */
    private final MiniGameEngine engine;

    public MiniRoom(GameType gameType,
                    String aSession, int aUserId, String aName,
                    String bSession, int bUserId, String bName) {
        this.gameType = gameType;
        this.playerASession = aSession;
        this.playerAUserId = aUserId;
        this.playerAName = aName == null || aName.isEmpty() ? ("玩家" + aUserId) : aName;
        this.playerBSession = bSession;
        this.playerBUserId = bUserId;
        this.playerBName = bName == null || bName.isEmpty() ? ("玩家" + bUserId) : bName;
        // 根据游戏类型创建对应引擎，新增棋类只需在此追加一行
        this.engine = gameType == GameType.CHESS ? new ChessEngine() : new GomokuEngine();
    }

    public String getRoomId() {
        return roomId;
    }

    public GameType getGameType() {
        return gameType;
    }

    /**
     * 获取统一引擎实例。
     * <p>
     * Handler 通过此方法多态调用落子、认输、快照等操作，无需关心具体棋类。
     */
    public MiniGameEngine getEngine() {
        return engine;
    }

    public String getPlayerASession() {
        return playerASession;
    }

    public String getPlayerBSession() {
        return playerBSession;
    }

    public int getPlayerAUserId() {
        return playerAUserId;
    }

    public int getPlayerBUserId() {
        return playerBUserId;
    }

    public String getPlayerAName() {
        return playerAName;
    }

    public String getPlayerBName() {
        return playerBName;
    }

    public boolean containsSession(String sessionId) {
        return playerASession.equals(sessionId) || playerBSession.equals(sessionId);
    }

    public String opponentSession(String sessionId) {
        if (playerASession.equals(sessionId)) {
            return playerBSession;
        }
        if (playerBSession.equals(sessionId)) {
            return playerASession;
        }
        return null;
    }

    /**
     * 判断指定会话是否为 A 方（先手）。
     * <p>
     * 五子棋中 A 执黑先手，象棋中 A 执红先手。
     */
    public boolean isSideA(String sessionId) {
        return playerASession.equals(sessionId);
    }
}

