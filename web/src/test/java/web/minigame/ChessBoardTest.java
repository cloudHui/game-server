package web.minigame;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChessBoardTest {
    @Test
    public void checkedSideCanOnlyResolveCheck() throws Exception {
        ChessBoard board = board(
                "...kr....",
                ".........",
                ".........",
                ".........",
                "....R....",
                ".........",
                ".........",
                ".........",
                ".........",
                "....K....");
        List<int[]> moves = board.legalMovesFrom(4, 4);
        assertFalse(moves.isEmpty());
        for (int[] move : moves) assertEquals(4, move[1]);
    }

    @Test
    public void checkmateEndsGameWithoutCapturingKing() throws Exception {
        ChessBoard board = board(
                "....k....",
                "...R.R...",
                "...R.....",
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                "....K....");
        assertTrue(board.move(2, 3, 2, 4, true));
        assertTrue(board.isFinished());
        assertEquals("red", board.getWinner());
        assertEquals("将死", board.getEndReason());
        assertFalse(board.move(2, 4, 0, 4, false));
    }

    @Test
    public void kingCannotBeCapturedDirectly() throws Exception {
        ChessBoard board = board(
                "....k....",
                ".........",
                "....R....",
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                ".........",
                "...K.....");
        assertFalse(board.move(2, 4, 0, 4, true));
        assertFalse(board.isFinished());
    }

    private static ChessBoard board(String... rows) throws Exception {
        ChessBoard board = new ChessBoard();
        Field field = ChessBoard.class.getDeclaredField("cells");
        field.setAccessible(true);
        char[][] cells = (char[][]) field.get(board);
        for (int row = 0; row < ChessBoard.ROWS; row++) {
            cells[row] = rows[row].toCharArray();
        }
        return board;
    }
}
