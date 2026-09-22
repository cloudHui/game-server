package com.cloud.hub.web.minigame.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋简易规则：红方在下（行 7-9），黑方在上（行 0-2）。
 * 棋子：K帅/将 A仕 B相 N马 R车 C炮 P兵；大写红，小写黑。
 */
public class ChessBoard {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final char[][] cells = new char[ROWS][COLS];
    /**
     * true=红方回合
     */
    private boolean redTurn = true;
    private boolean finished;
    private String winner; // "red" | "black" | "draw"
    private String endReason = "";

    public ChessBoard() {
        reset();
    }

    public void reset() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells[r][c] = '.';
            }
        }
        String black = "rnbakabnr";
        String red = "RNBAKABNR";
        for (int c = 0; c < COLS; c++) {
            cells[0][c] = black.charAt(c);
            cells[9][c] = red.charAt(c);
        }
        cells[2][1] = 'c';
        cells[2][7] = 'c';
        cells[7][1] = 'C';
        cells[7][7] = 'C';
        for (int c = 0; c < COLS; c += 2) {
            cells[3][c] = 'p';
            cells[6][c] = 'P';
        }
        redTurn = true;
        finished = false;
        winner = null;
        endReason = "";
    }

    public boolean isRedTurn() {
        return redTurn;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getWinner() {
        return winner;
    }

    public String getEndReason() {
        return endReason;
    }

    public String boardString() {
        StringBuilder sb = new StringBuilder(ROWS * COLS);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(cells[r][c]);
            }
        }
        return sb.toString();
    }

    public static boolean isRedPiece(char ch) {
        return Character.isUpperCase(ch);
    }

    public static boolean isBlackPiece(char ch) {
        return Character.isLowerCase(ch) && ch != '.';
    }

    public boolean move(int fr, int fc, int tr, int tc, boolean redSide) {
        if (finished || redSide != redTurn) {
            return false;
        }
        if (!inBoard(fr, fc) || !inBoard(tr, tc)) {
            return false;
        }
        char piece = cells[fr][fc];
        if (piece == '.' || (redTurn && !isRedPiece(piece)) || (!redTurn && !isBlackPiece(piece))) {
            return false;
        }
        char target = cells[tr][tc];
        if (redTurn && isRedPiece(target)) return false;
        if (!redTurn && isBlackPiece(target)) return false;

        if (!validatePieceMove(piece, fr, fc, tr, tc, target)) {
            return false;
        }

        cells[tr][tc] = piece;
        cells[fr][fc] = '.';

        if (target == 'k') {
            finished = true;
            winner = "red";
            endReason = "红方将死黑将";
            return true;
        }
        if (target == 'K') {
            finished = true;
            winner = "black";
            endReason = "黑方将死红帅";
            return true;
        }

        redTurn = !redTurn;
        return true;
    }

    public void resign(boolean redSide) {
        finished = true;
        winner = redSide ? "black" : "red";
        endReason = (redSide ? "红方" : "黑方") + "认输";
    }

    private static boolean inBoard(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    private boolean validatePieceMove(char piece, int fr, int fc, int tr, int tc, char target) {
        int dr = tr - fr;
        int dc = tc - fc;
        int adr = Math.abs(dr);
        int adc = Math.abs(dc);
        char type = Character.toUpperCase(piece);
        boolean isRed = isRedPiece(piece);

        switch (type) {
            case 'K': // 将/帅：九宫内一步直线
                if (!inPalace(tr, tc, isRed)) return false;
                return (adr == 1 && adc == 0) || (adr == 0 && adc == 1);

            case 'A': // 仕/士：九宫内一步斜线
                if (!inPalace(tr, tc, isRed)) return false;
                return adr == 1 && adc == 1;

            case 'B': // 相/象：田字，不能过河，塞相眼
                if (isRed && tr < 5) return false;
                if (!isRed && tr > 4) return false;
                if (adr != 2 || adc != 2) return false;
                int eyeR = (fr + tr) / 2;
                int eyeC = (fc + tc) / 2;
                return cells[eyeR][eyeC] == '.';

            case 'N': // 马：日字，蹩马腿
                if (!((adr == 2 && adc == 1) || (adr == 1 && adc == 2))) return false;
                int legR = adr == 2 ? fr + dr / 2 : fr;
                int legC = adc == 2 ? fc + dc / 2 : fc;
                return cells[legR][legC] == '.';

            case 'R': // 车：直线，无阻挡
                if (adr != 0 && adc != 0) return false;
                return countBetween(fr, fc, tr, tc) == 0;

            case 'C': // 炮：直线，走不吃子无阻挡，吃子隔一子
                if (adr != 0 && adc != 0) return false;
                int between = countBetween(fr, fc, tr, tc);
                if (target == '.') {
                    return between == 0;
                } else {
                    return between == 1;
                }

            case 'P': // 兵/卒：向前一步；过河后可左右一步；不可后退
                if (isRed) {
                    if (dr > 0) return false; // 红兵只能向上（行减小）
                    if (fr >= 5) {
                        // 未过河：只能向前
                        return dr == -1 && dc == 0;
                    } else {
                        // 已过河：可向前或左右
                        return (dr == -1 && dc == 0) || (dr == 0 && adc == 1);
                    }
                } else {
                    if (dr < 0) return false; // 黑卒只能向下（行增加）
                    if (fr <= 4) {
                        return dr == 1 && dc == 0;
                    } else {
                        return (dr == 1 && dc == 0) || (dr == 0 && adc == 1);
                    }
                }

            default:
                return false;
        }
    }

    private static boolean inPalace(int r, int c, boolean red) {
        if (c < 3 || c > 5) return false;
        if (red) {
            return r >= 7 && r <= 9;
        } else {
            return r >= 0 && r <= 2;
        }
    }

    private int countBetween(int fr, int fc, int tr, int tc) {
        int cnt = 0;
        int stepR = Integer.compare(tr, fr);
        int stepC = Integer.compare(tc, fc);
        int cr = fr + stepR;
        int cc = fc + stepC;
        while (cr != tr || cc != tc) {
            if (cells[cr][cc] != '.') cnt++;
            cr += stepR;
            cc += stepC;
        }
        return cnt;
    }
}
