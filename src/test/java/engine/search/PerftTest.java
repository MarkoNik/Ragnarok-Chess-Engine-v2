package engine.search;

import engine.core.bitboard.BitboardHelper;
import engine.core.state.GameState;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Move generator correctness, verified via Perft against the standard CPW test
 * positions/node-counts (chessprogramming.org/Perft_Results). A mismatch here
 * means the move generator is missing, adding, or mis-generating moves for
 * some rule (castling rights, en passant, pins, promotions, check evasion...).
 */
class PerftTest {
    private static final String STARTPOS = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String POSITION_4 = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String POSITION_5 = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";
    private static final String POSITION_6 = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

    private static final MoveGenerator MOVE_GENERATOR = new MoveGenerator(new BitboardHelper());

    static Stream<Arguments> fastPositions() {
        return Stream.of(
                Arguments.of("startpos depth 1", STARTPOS, 1, 20L),
                Arguments.of("startpos depth 2", STARTPOS, 2, 400L),
                Arguments.of("startpos depth 3", STARTPOS, 3, 8_902L),
                Arguments.of("startpos depth 4", STARTPOS, 4, 197_281L),

                Arguments.of("kiwipete depth 1", KIWIPETE, 1, 48L),
                Arguments.of("kiwipete depth 2", KIWIPETE, 2, 2_039L),
                Arguments.of("kiwipete depth 3", KIWIPETE, 3, 97_862L),

                Arguments.of("position 3 depth 1", POSITION_3, 1, 14L),
                Arguments.of("position 3 depth 2", POSITION_3, 2, 191L),
                Arguments.of("position 3 depth 3", POSITION_3, 3, 2_812L),
                Arguments.of("position 3 depth 4", POSITION_3, 4, 43_238L),

                Arguments.of("position 4 depth 1", POSITION_4, 1, 6L),
                Arguments.of("position 4 depth 2", POSITION_4, 2, 264L),
                Arguments.of("position 4 depth 3", POSITION_4, 3, 9_467L),

                Arguments.of("position 5 depth 1", POSITION_5, 1, 44L),
                Arguments.of("position 5 depth 2", POSITION_5, 2, 1_486L),
                Arguments.of("position 5 depth 3", POSITION_5, 3, 62_379L),

                Arguments.of("position 6 depth 1", POSITION_6, 1, 46L),
                Arguments.of("position 6 depth 2", POSITION_6, 2, 2_079L),
                Arguments.of("position 6 depth 3", POSITION_6, 3, 89_890L)
        );
    }

    static Stream<Arguments> slowPositions() {
        return Stream.of(
                Arguments.of("startpos depth 5", STARTPOS, 5, 4_865_609L),
                Arguments.of("kiwipete depth 4", KIWIPETE, 4, 4_085_603L),
                Arguments.of("position 3 depth 5", POSITION_3, 5, 674_624L),
                Arguments.of("position 4 depth 4", POSITION_4, 4, 422_333L),
                Arguments.of("position 5 depth 4", POSITION_5, 4, 2_103_487L),
                Arguments.of("position 6 depth 4", POSITION_6, 4, 3_894_594L)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fastPositions")
    void perftMatchesKnownNodeCounts(String name, String fen, int depth, long expectedNodes) {
        GameState gameState = FenParser.parseFEN(fen);
        assertEquals(expectedNodes, perft(gameState, depth), name);
    }

    @Tag("slow")
    @ParameterizedTest(name = "{0}")
    @MethodSource("slowPositions")
    void perftMatchesKnownNodeCounts_deep(String name, String fen, int depth, long expectedNodes) {
        GameState gameState = FenParser.parseFEN(fen);
        assertEquals(expectedNodes, perft(gameState, depth), name);
    }

    private static long perft(GameState gameState, int depth) {
        if (depth == 0) {
            return 1;
        }

        MOVE_GENERATOR.setBitboard(gameState.getBitboard());
        int[] moves = MOVE_GENERATOR.generateLegalMoves(gameState.isWhiteTurn()).clone();
        int moveCount = MOVE_GENERATOR.getMoveCounter();
        MOVE_GENERATOR.clearMoves();

        long nodes = 0;
        for (int i = 0; i < moveCount; i++) {
            gameState.getBitboard().backupState();
            gameState.playMove(moves[i]);
            nodes += perft(gameState, depth - 1);
            gameState.getBitboard().restoreState();
            gameState.switchPlayer();
        }
        return nodes;
    }
}
