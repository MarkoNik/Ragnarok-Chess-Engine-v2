package engine.search;

import app.Constants;
import engine.config.EngineConfig;
import engine.core.bitboard.BitboardHelper;
import engine.core.state.Bitboard;
import engine.core.state.TranspositionTable;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimaxTest {

    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

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
        Minimax minimax = new Minimax(moveGenerator, new Evaluator(), transpositionTable, new MoveOrderer(), new EngineConfig());

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

    /**
     * EngineConfig's NullMovePruning flag must actually change search
     * behavior, not just exist. Verified manually earlier (Kiwipete at depth
     * 6: 342546 nodes with, 536441 without, same best move either way); this
     * automates that same comparison at a smaller depth so it runs fast.
     */
    @Test
    void nullMovePruning_flagOff_searchesMoreNodesThanFlagOn() {
        int depth = 5;

        EngineConfig withNullMove = new EngineConfig();
        SearchStats withStats = runSearch(KIWIPETE, depth, withNullMove);

        EngineConfig withoutNullMove = new EngineConfig();
        withoutNullMove.set("NullMovePruning", false);
        SearchStats withoutStats = runSearch(KIWIPETE, depth, withoutNullMove);

        assertTrue(withStats.getNullMoveCutoffs() > 0, "expected at least one null-move cutoff with the flag on");
        assertEquals(0, withoutStats.getNullMoveCutoffs(), "expected zero null-move cutoffs with the flag off");
        assertTrue(withStats.getNodesSearched() < withoutStats.getNodesSearched(),
                "null-move pruning should reduce nodes searched at the same depth: with="
                        + withStats.getNodesSearched() + " without=" + withoutStats.getNodesSearched());
    }

    private static SearchStats runSearch(String fen, int depth, EngineConfig config) {
        MoveGenerator moveGenerator = new MoveGenerator(new BitboardHelper());
        Minimax minimax = new Minimax(moveGenerator, new Evaluator(), new TranspositionTable(), new MoveOrderer(), config);
        Bitboard bitboard = FenParser.parseFEN(fen).getBitboard();
        moveGenerator.setBitboard(bitboard);
        minimax.setBitboard(bitboard);
        return minimax.initiateSearch(depth, true);
    }
}
