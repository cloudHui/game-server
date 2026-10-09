package com.cloud.hub.web.minigame.ai;

import com.cloud.hub.web.minigame.chess.ChessBoard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 中国象棋轻量级 AI 决策引擎。
 * <p>
 * 基于棋子价值评估（车>炮/马>相/士>兵）、吃子优先及位置威胁启发式算法，
 * 兼具战术防守与主动进攻能力，专用于快速人机对弈与玩家陪练。
 * </p>
 *
 * @author cloud
 */
public final class ChessAi {

    private static final Random RNG = new Random();

    private ChessAi() {
    }

    /**
     * 计算并推荐最佳象棋走法。
     *
     * @param board 当前象棋棋盘
     * @param isRed 是否为红方
     * @return 包含最佳 [fr, fc, tr, tc] 的数组；若无合法走法则返回 null
     */
    public static int[] bestMove(ChessBoard board, boolean isRed) {
        if (board == null || board.isFinished() || board.isRedTurn() != isRed) {
            return null;
        }

        List<int[]> legalMoves = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;

        for (int fr = 0; fr < ChessBoard.ROWS; fr++) {
            for (int fc = 0; fc < ChessBoard.COLS; fc++) {
                char p = board.getPieceAt(fr, fc);
                if (p == '.' || (isRed && !ChessBoard.isRedPiece(p)) || (!isRed && !ChessBoard.isBlackPiece(p))) {
                    continue;
                }

                // 探测所有可能的合法走法
                for (int tr = 0; tr < ChessBoard.ROWS; tr++) {
                    for (int tc = 0; tc < ChessBoard.COLS; tc++) {
                        if (board.canMove(fr, fc, tr, tc, isRed)) {
                            int score = evaluateMove(board, p, fr, fc, tr, tc, isRed);
                            legalMoves.add(new int[]{fr, fc, tr, tc, score});
                            if (score > bestScore) {
                                bestScore = score;
                                bestMove = new int[]{fr, fc, tr, tc};
                            }
                        }
                    }
                }
            }
        }

        if (bestMove != null) {
            return bestMove;
        }
        if (!legalMoves.isEmpty()) {
            int[] fallback = legalMoves.get(RNG.nextInt(legalMoves.size()));
            return new int[]{fallback[0], fallback[1], fallback[2], fallback[3]};
        }
        return null;
    }

    private static int evaluateMove(ChessBoard board, char piece, int fr, int fc, int tr, int tc, boolean isRed) {
        char target = board.getPieceAt(tr, tc);
        int score = 0;

        // 1. 吃子收益
        if (target != '.') {
            score += pieceValue(target) * 10;
        }

        // 2. 兵卒过河推进奖励
        char lower = Character.toLowerCase(piece);
        if (lower == 'p') {
            if (isRed && tr < 5) {
                score += (5 - tr) * 8; // 红兵过河深入
            } else if (!isRed && tr > 4) {
                score += (tr - 4) * 8; // 黑卒过河深入
            }
        }

        // 3. 走中线控盘优势
        if (tc >= 3 && tc <= 5) {
            score += 6;
        }

        // 4. 微小随机扰动（避免走法机械千篇一律）
        score += RNG.nextInt(5);

        return score;
    }

    private static int pieceValue(char ch) {
        switch (Character.toLowerCase(ch)) {
            case 'k': return 10000; // 将/帅（绝杀权重）
            case 'r': return 900;   // 车
            case 'c': return 450;   // 炮
            case 'n': return 400;   // 马
            case 'b': return 200;   // 相/象
            case 'a': return 200;   // 仕/士
            case 'p': return 100;   // 兵/卒
            default:  return 0;
        }
    }
}
