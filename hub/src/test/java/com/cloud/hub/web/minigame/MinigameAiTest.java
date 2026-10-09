package com.cloud.hub.web.minigame;

import com.cloud.hub.web.minigame.ai.ChessAi;
import com.cloud.hub.web.minigame.ai.GomokuAi;
import com.cloud.hub.web.minigame.chess.ChessBoard;
import com.cloud.hub.web.minigame.gomoku.GomokuBoard;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 五子棋与中国象棋启发式 AI 引擎自动化测试。
 *
 * @author cloud
 */
public class MinigameAiTest {

    @Test
    public void testGomokuAiOpeningAndBlocking() {
        GomokuBoard board = new GomokuBoard();

        // 1. 空棋盘 AI 作为黑棋开局应落子天元 (7, 7)
        int[] firstMove = GomokuAi.bestMove(board.snapshot(), GomokuBoard.BLACK);
        assertNotNull(firstMove);
        assertEquals(7, firstMove[0]);
        assertEquals(7, firstMove[1]);

        // 2. 模拟黑棋即将连五 (4 连)，白棋 AI 必须进行强防守封堵
        board.place(1, 1, GomokuBoard.BLACK);
        board.place(0, 0, GomokuBoard.WHITE); // 白棋垫步
        board.place(2, 1, GomokuBoard.BLACK);
        board.place(0, 1, GomokuBoard.WHITE);
        board.place(3, 1, GomokuBoard.BLACK);
        board.place(0, 2, GomokuBoard.WHITE);
        board.place(4, 1, GomokuBoard.BLACK);

        int[] defenseMove = GomokuAi.bestMove(board.snapshot(), GomokuBoard.WHITE);
        assertNotNull(defenseMove);
        // 防守落点应在两端之一：(0, 1) 已被占，所以应在 (5, 1)
        assertEquals(5, defenseMove[0]);
        assertEquals(1, defenseMove[1]);
    }

    @Test
    public void testChessAiLegalMoveAndCapture() {
        ChessBoard board = new ChessBoard();

        // 红方先手，计算 AI 决策
        int[] redMove = ChessAi.bestMove(board, true);
        assertNotNull(redMove);
        assertEquals(4, redMove.length);

        // 验证移动合法性
        int fr = redMove[0];
        int fc = redMove[1];
        int tr = redMove[2];
        int tc = redMove[3];
        assertTrue("AI 生成的起点应有红方棋子", ChessBoard.isRedPiece(board.getPieceAt(fr, fc)));
        assertTrue("AI 生成的落子应符合规则", board.canMove(fr, fc, tr, tc, true));

        // 实际走子并切换至黑方回合
        boolean moved = board.move(fr, fc, tr, tc, true);
        assertTrue(moved);
        assertFalse(board.isRedTurn());

        // 黑方 AI 决策走子
        int[] blackMove = ChessAi.bestMove(board, false);
        assertNotNull(blackMove);
        assertTrue("AI 生成的黑方走子合法", board.canMove(blackMove[0], blackMove[1], blackMove[2], blackMove[3], false));
    }
}
