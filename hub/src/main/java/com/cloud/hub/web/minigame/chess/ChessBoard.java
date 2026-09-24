package com.cloud.hub.web.minigame.chess;

/**
 * 中国象棋简易棋盘规则与落子合法性校验。
 * <p>
 * 采用 10 行 9 列棋盘矩阵：红方在下（行 7-9），黑方在上（行 0-2）。
 * 棋子标识：K帅/将 A仕 B相 N马 R车 C炮 P兵；大写字母为红方，小写字母为黑方。
 *
 * @author cloud
 */
public class ChessBoard {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final char[][] cells = new char[ROWS][COLS];
    private boolean redTurn = true;
    private boolean finished;
    private String winner;
    private String endReason = "";

    public ChessBoard() {
        reset();
    }

    /**
     * 重置棋盘到初始开局布局。
     */
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

    /**
     * 执行一步走棋。
     *
     * @param fr      起始行
     * @param fc      起始列
     * @param tr      目标行
     * @param tc      目标列
     * @param redSide 是否为红方操作
     * @return 走棋是否合法且成功执行
     */
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
        if ((redTurn && isRedPiece(target)) || (!redTurn && isBlackPiece(target))) {
            return false;
        }
        if (!validatePieceMove(piece, fr, fc, tr, tc, target)) {
            return false;
        }

        cells[tr][tc] = piece;
        cells[fr][fc] = '.';
        return checkKingCaptured(target);
    }

    /**
     * 判定是否吃掉对方帅/将终局。
     */
    private boolean checkKingCaptured(char target) {
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

    /**
     * 认输终局。
     *
     * @param redSide 是否为红方认输
     */
    public void resign(boolean redSide) {
        finished = true;
        winner = redSide ? "black" : "red";
        endReason = (redSide ? "红方" : "黑方") + "认输";
    }

    private static boolean inBoard(int r, int c) {
        return r >= 0 && r < ROWS && c >= 0 && c < COLS;
    }

    /**
     * 校验具体棋子走法规则（控制在 35 行以内）。
     */
    private boolean validatePieceMove(char piece, int fr, int fc, int tr, int tc, char target) {
        int dr = tr - fr;
        int dc = tc - fc;
        int adr = Math.abs(dr);
        int adc = Math.abs(dc);
        char type = Character.toUpperCase(piece);
        boolean isRed = isRedPiece(piece);

        switch (type) {
            case 'K': // 将/帅：九宫内一步直线
                return inPalace(tr, tc, isRed) && ((adr == 1 && adc == 0) || (adr == 0 && adc == 1));
            case 'A': // 仕/士：九宫内一步斜线
                return inPalace(tr, tc, isRed) && adr == 1 && adc == 1;
            case 'B': // 相/象：田字不跨河，塞相眼
                return validateElephant(fr, fc, tr, tc, dr, dc, isRed);
            case 'N': // 马：日字，蹩马腿
                return validateHorse(fr, fc, dr, dc, adr, adc);
            case 'R': // 车：直线无阻挡
                return (adr == 0 || adc == 0) && countBetween(fr, fc, tr, tc) == 0;
            case 'C': // 炮：直线，走不吃无阻挡，吃子隔一子
                return validateCannon(fr, fc, tr, tc, adr, adc, target);
            case 'P': // 兵/卒
                return validatePawnMove(fr, dr, dc, adc, isRed);
            default:
                return false;
        }
    }

    private boolean validateElephant(int fr, int fc, int tr, int tc, int dr, int dc, boolean isRed) {
        if ((isRed && tr < 5) || (!isRed && tr > 4) || Math.abs(dr) != 2 || Math.abs(dc) != 2) {
            return false;
        }
        return cells[(fr + tr) / 2][(fc + tc) / 2] == '.';
    }

    private boolean validateHorse(int fr, int fc, int dr, int dc, int adr, int adc) {
        if (!((adr == 2 && adc == 1) || (adr == 1 && adc == 2))) {
            return false;
        }
        int legR = adr == 2 ? fr + dr / 2 : fr;
        int legC = adc == 2 ? fc + dc / 2 : fc;
        return cells[legR][legC] == '.';
    }

    private boolean validateCannon(int fr, int fc, int tr, int tc, int adr, int adc, char target) {
        if (adr != 0 && adc != 0) {
            return false;
        }
        int between = countBetween(fr, fc, tr, tc);
        return target == '.' ? between == 0 : between == 1;
    }

    private boolean validatePawnMove(int fr, int dr, int dc, int adc, boolean isRed) {
        if (isRed) {
            if (dr > 0) return false;
            return fr >= 5 ? (dr == -1 && dc == 0) : ((dr == -1 && dc == 0) || (dr == 0 && adc == 1));
        } else {
            if (dr < 0) return false;
            return fr <= 4 ? (dr == 1 && dc == 0) : ((dr == 1 && dc == 0) || (dr == 0 && adc == 1));
        }
    }

    private static boolean inPalace(int r, int c, boolean red) {
        if (c < 3 || c > 5) {
            return false;
        }
        return red ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    private int countBetween(int fr, int fc, int tr, int tc) {
        int cnt = 0;
        int stepR = Integer.compare(tr, fr);
        int stepC = Integer.compare(tc, fc);
        int cr = fr + stepR;
        int cc = fc + stepC;
        while (cr != tr || cc != tc) {
            if (cells[cr][cc] != '.') {
                cnt++;
            }
            cr += stepR;
            cc += stepC;
        }
        return cnt;
    }
}
