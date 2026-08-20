package com.cloud.web.minigame;

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

    public char[][] snapshot() {
        char[][] copy = new char[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(cells[r], 0, copy[r], 0, COLS);
        }
        return copy;
    }

    public String boardString() {
        StringBuilder sb = new StringBuilder(ROWS * COLS);
        for (int r = 0; r < ROWS; r++) {
            sb.append(cells[r]);
        }
        return sb.toString();
    }

    public boolean move(int fr, int fc, int tr, int tc, boolean asRed) {
        if (finished || asRed != redTurn) {
            return false;
        }
        if (!inBoard(fr, fc) || !inBoard(tr, tc)) {
            return false;
        }
        char p = cells[fr][fc];
        if (p == '.' || isRed(p) != asRed) {
            return false;
        }
        if (!isMoveLegal(fr, fc, tr, tc)) {
            return false;
        }
        cells[tr][tc] = p;
        cells[fr][fc] = '.';
        redTurn = !redTurn;
        if (isCheckmate(redTurn)) {
            finished = true;
            winner = asRed ? "red" : "black";
            endReason = "将死";
        }
        return true;
    }

    public void resign(boolean redResigns) {
        if (finished) {
            return;
        }
        finished = true;
        winner = redResigns ? "black" : "red";
        endReason = "认输";
    }

    public List<int[]> legalMovesFrom(int r, int c) {
        List<int[]> list = new ArrayList<>();
        if (!inBoard(r, c) || cells[r][c] == '.') {
            return list;
        }
        for (int tr = 0; tr < ROWS; tr++) {
            for (int tc = 0; tc < COLS; tc++) {
                if (isMoveLegal(r, c, tr, tc)) list.add(new int[]{tr, tc});
            }
        }
        return list;
    }

    private boolean isLegalMove(int fr, int fc, int tr, int tc) {
        char p = cells[fr][fc];
        char t = cells[tr][tc];
        if (t != '.' && isRed(t) == isRed(p)) {
            return false;
        }
        if (Character.toLowerCase(t) == 'k') return false;
        return isPseudoLegal(fr, fc, tr, tc);
    }

    private boolean isPseudoLegal(int fr, int fc, int tr, int tc) {
        char p = cells[fr][fc];
        char t = cells[tr][tc];
        if (t != '.' && isRed(t) == isRed(p)) return false;
        int dr = tr - fr;
        int dc = tc - fc;
        char type = Character.toLowerCase(p);
        switch (type) {
            case 'k':
                return inPalace(tr, tc, isRed(p)) && Math.abs(dr) + Math.abs(dc) == 1;
            case 'a':
                return inPalace(tr, tc, isRed(p)) && Math.abs(dr) == 1 && Math.abs(dc) == 1;
            case 'b':
                return Math.abs(dr) == 2 && Math.abs(dc) == 2
                        && cells[fr + dr / 2][fc + dc / 2] == '.'
                        && (isRed(p) ? tr >= 5 : tr <= 4);
            case 'n': {
                if (!((Math.abs(dr) == 2 && Math.abs(dc) == 1) || (Math.abs(dr) == 1 && Math.abs(dc) == 2))) {
                    return false;
                }
                int br = Math.abs(dr) == 2 ? fr + dr / 2 : fr;
                int bc = Math.abs(dc) == 2 ? fc + dc / 2 : fc;
                return cells[br][bc] == '.';
            }
            case 'r':
                return (dr == 0 || dc == 0) && clearPath(fr, fc, tr, tc);
            case 'c': {
                if (dr != 0 && dc != 0) {
                    return false;
                }
                int blocks = countBlocks(fr, fc, tr, tc);
                if (t == '.') {
                    return blocks == 0;
                }
                return blocks == 1;
            }
            case 'p':
                if (isRed(p)) {
                    if (fr >= 5) {
                        return dr == -1 && dc == 0;
                    }
                    return (dr == -1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
                }
                if (fr <= 4) {
                    return dr == 1 && dc == 0;
                }
                return (dr == 1 && dc == 0) || (dr == 0 && Math.abs(dc) == 1);
            default:
                return false;
        }
    }

    private boolean isMoveLegal(int fr, int fc, int tr, int tc) {
        if (!isLegalMove(fr, fc, tr, tc)) return false;
        char piece = cells[fr][fc];
        char captured = cells[tr][tc];
        cells[tr][tc] = piece;
        cells[fr][fc] = '.';
        boolean invalid = faceToFace() || isInCheck(isRed(piece));
        cells[fr][fc] = piece;
        cells[tr][tc] = captured;
        return !invalid;
    }

    private boolean isInCheck(boolean red) {
        char king = red ? 'K' : 'k';
        int kingRow = -1, kingCol = -1;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c] == king) {
                    kingRow = r;
                    kingCol = c;
                }
            }
        if (kingRow < 0 || faceToFace()) return true;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                char attacker = cells[r][c];
                if (attacker != '.' && isRed(attacker) != red
                        && isPseudoLegal(r, c, kingRow, kingCol)) return true;
            }
        return false;
    }

    private boolean isCheckmate(boolean red) {
        if (!isInCheck(red)) return false;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c] != '.' && isRed(cells[r][c]) == red
                        && !legalMovesFrom(r, c).isEmpty()) return false;
            }
        return true;
    }

    private boolean clearPath(int fr, int fc, int tr, int tc) {
        return countBlocks(fr, fc, tr, tc) == 0;
    }

    private int countBlocks(int fr, int fc, int tr, int tc) {
        int dr = Integer.compare(tr, fr);
        int dc = Integer.compare(tc, fc);
        int r = fr + dr;
        int c = fc + dc;
        int n = 0;
        while (r != tr || c != tc) {
            if (cells[r][c] != '.') {
                n++;
            }
            r += dr;
            c += dc;
        }
        return n;
    }

    private boolean faceToFace() {
        int rk = -1, rc = -1, bk = -1, bc = -1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (cells[r][c] == 'K') {
                    rk = r;
                    rc = c;
                } else if (cells[r][c] == 'k') {
                    bk = r;
                    bc = c;
                }
            }
        }
        if (rk < 0 || bk < 0 || rc != bc) {
            return false;
        }
        return countBlocks(rk, rc, bk, bc) == 0;
    }

    private static boolean inPalace(int r, int c, boolean red) {
        if (c < 3 || c > 5) {
            return false;
        }
        return red ? r >= 7 && r <= 9 : r >= 0 && r <= 2;
    }

    private static boolean inBoard(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    private static boolean isRed(char p) {
        return p >= 'A' && p <= 'Z';
    }
}
