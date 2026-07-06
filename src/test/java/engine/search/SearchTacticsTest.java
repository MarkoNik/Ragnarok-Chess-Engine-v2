package engine.search;

import engine.core.bitboard.BitboardHelper;
import engine.core.state.Bitboard;
import engine.core.state.TranspositionTable;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A small tactics suite: can the search actually find forced/obvious best
 * moves, and does the move generator correctly recognize stalemate. Every
 * position here was independently verified (Stockfish for the tactical
 * positions - "score mate 1"/best move matched; python-chess for the
 * stalemate position - is_stalemate()==True, legal_moves==[]) rather than
 * hand-derived, since a wrong expected value here would make for a silently
 * wrong regression test.
 */
class SearchTacticsTest {
    private static final int SEARCH_DEPTH = 4;

    /** Minimax and MoveGenerator each hold their own Bitboard reference; both must be set. */
    private static Minimax newMinimax(Bitboard bitboard) {
        MoveGenerator moveGenerator = new MoveGenerator(new BitboardHelper());
        moveGenerator.setBitboard(bitboard);
        Minimax minimax = new Minimax(moveGenerator, new Evaluator(), new TranspositionTable(), new MoveOrderer());
        minimax.setBitboard(bitboard);
        return minimax;
    }

    @Test
    void findsBackRankMateInOne() {
        // White: Ra1, Kg1. Black: Kg8, pawns f7/g7/h7 (boxed in). 1.Ra8# -
        // verified with Stockfish: "info depth 1 ... score mate 1 ... pv a1a8".
        Bitboard bitboard = FenParser.parseFEN("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1").getBitboard();
        Minimax minimax = newMinimax(bitboard);

        minimax.initiateSearch(SEARCH_DEPTH, true);

        assertEquals("a1a8", FenParser.moveToAlgebraic(minimax.getBestMove()));
        assertTrue(minimax.getBestEval() > 9000,
                "expected a mate-scale evaluation, got " + minimax.getBestEval());
    }

    @Test
    void findsHangingQueenCapture() {
        // White Nc3, Black Qd5 undefended, Black just a lone king otherwise.
        // Verified with Stockfish: bestmove c3d5 at every depth 1-6.
        Bitboard bitboard = FenParser.parseFEN("4k3/8/8/3q4/8/2N5/8/4K3 w - - 0 1").getBitboard();
        Minimax minimax = newMinimax(bitboard);

        minimax.initiateSearch(SEARCH_DEPTH, true);

        assertEquals("c3d5", FenParser.moveToAlgebraic(minimax.getBestMove()));
    }

    @Test
    void moveGenerator_recognizesStalemate() {
        // Classic K+Q vs K stalemate: Black king a8, White queen b6, White
        // king b1. Black to move, not in check, and has no legal move.
        // Verified with python-chess: is_stalemate()==True, legal_moves==[].
        Bitboard bitboard = FenParser.parseFEN("k7/8/1Q6/8/8/8/8/1K6 b - - 0 1").getBitboard();
        MoveGenerator moveGenerator = new MoveGenerator(new BitboardHelper());
        moveGenerator.setBitboard(bitboard);

        moveGenerator.generateLegalMoves(false);
        int moveCount = moveGenerator.getMoveCounter();
        boolean inCheck = moveGenerator.isKingInCheck(false);
        moveGenerator.clearMoves();

        assertEquals(0, moveCount, "stalemate position should have no legal moves");
        assertFalse(inCheck, "stalemate position must not be check (otherwise it'd be checkmate)");
    }
}
