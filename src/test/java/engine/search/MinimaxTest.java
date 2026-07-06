package engine.search;

import app.Constants;
import engine.core.bitboard.BitboardHelper;
import engine.core.state.Bitboard;
import engine.core.state.TranspositionTable;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimaxTest {

    /**
     * getPrincipalVariation used to compute maxDepth as max(depth, 10) instead
     * of min(depth, 10). Since EngineState passes depth=INF whenever a "go" is
     * driven by movetime/wtime rather than an explicit depth, that meant the PV
     * walk was effectively unbounded and would follow a transposition table
     * chain of "best move" links for as long as one existed - overflowing
     * Bitboard's fixed-size backup stack on a long enough chain and crashing
     * the engine. This builds a real, playable 15-ply chain of TT entries and
     * checks the walk stops at 10 regardless of how large the depth argument is.
     */
    @Test
    void getPrincipalVariation_stopsAtTenPlies_evenWithALongerAvailableChain() {
        MoveGenerator moveGenerator = new MoveGenerator(new BitboardHelper());
        TranspositionTable transpositionTable = new TranspositionTable();
        Minimax minimax = new Minimax(moveGenerator, new Evaluator(), transpositionTable, new MoveOrderer());

        Bitboard bitboard = FenParser.parseFEN(Constants.INITIAL_FEN).getBitboard();
        moveGenerator.setBitboard(bitboard);
        minimax.setBitboard(bitboard);

        int chainLength = 15;
        boolean isWhiteTurn = true;
        for (int ply = 0; ply < chainLength; ply++) {
            long hashBeforeMove = bitboard.getHash();
            moveGenerator.setBitboard(bitboard);
            int[] moves = moveGenerator.generateLegalMoves(isWhiteTurn).clone();
            int move = moves[0]; // arbitrary; just needs to be a real legal move
            moveGenerator.clearMoves();

            transpositionTable.put(hashBeforeMove, move, 0, 1, transpositionTable.EXACT);

            bitboard.backupState();
            bitboard.makeMove(move, isWhiteTurn, false);
            isWhiteTurn = !isWhiteTurn;
        }
        for (int i = 0; i < chainLength; i++) {
            bitboard.restoreState();
        }
        isWhiteTurn = true;

        List<Integer> pv = minimax.getPrincipalVariation(1_000_000_000, isWhiteTurn);

        assertEquals(10, pv.size(),
                "getPrincipalVariation must cap at 10 plies even when a longer TT chain is available "
                        + "and a huge depth argument is passed in");
    }
}
