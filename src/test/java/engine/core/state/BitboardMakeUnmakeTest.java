package engine.core.state;

import engine.core.bitboard.BitboardHelper;
import engine.search.MoveGenerator;
import engine.util.Zobrist;
import engine.util.bits.FenParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * For every legal move reachable within a few plies of several known positions:
 *  1) backupState()/makeMove()/restoreState() must return the board to a
 *     byte-identical state (pieces, occupancies, castle flags, en passant
 *     square, hash) - i.e. make/unmake must be a true inverse pair.
 *  2) the incrementally-updated Zobrist hash after a move must match a full
 *     from-scratch recomputation (Zobrist.generateHash), catching any missed
 *     XOR in Bitboard.makeMove's incremental hash maintenance.
 *
 * These are exactly the two invariants the original code had commented-out
 * ad-hoc debug checks for (see Bitboard.makeMove and PerftDriver.search).
 */
class BitboardMakeUnmakeTest {
    private static final String STARTPOS = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String POSITION_4 = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String POSITION_5 = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";
    private static final String POSITION_6 = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

    private static final MoveGenerator MOVE_GENERATOR = new MoveGenerator(new BitboardHelper());
    private static final int DEPTH = 3;

    @ParameterizedTest
    @ValueSource(strings = {STARTPOS, KIWIPETE, POSITION_3, POSITION_4, POSITION_5, POSITION_6})
    void makeUnmake_isSymmetric_andHashStaysConsistent(String fen) {
        GameState gameState = FenParser.parseFEN(fen);

        // sanity: the hash computed by parseFEN must match a full recompute
        assertEquals(Zobrist.generateHash(gameState.getBitboard(), gameState.isWhiteTurn()),
                gameState.getBitboard().getHash(), "initial hash mismatch for " + fen);

        walk(gameState, DEPTH);
    }

    private static void walk(GameState gameState, int depth) {
        if (depth == 0) {
            return;
        }

        Bitboard bitboard = gameState.getBitboard();
        MOVE_GENERATOR.setBitboard(bitboard);
        int[] moves = MOVE_GENERATOR.generateLegalMoves(gameState.isWhiteTurn()).clone();
        int moveCount = MOVE_GENERATOR.getMoveCounter();
        MOVE_GENERATOR.clearMoves();

        for (int i = 0; i < moveCount; i++) {
            String moveDesc = FenParser.moveToAlgebraic(moves[i]);

            long[] piecesBefore = bitboard.getPieces().clone();
            long[] occupanciesBefore = bitboard.getOccupancies().clone();
            byte castlesBefore = bitboard.getCastlesFlags();
            int enPassantBefore = bitboard.getEnPassantSquare();
            long hashBefore = bitboard.getHash();

            bitboard.backupState();
            gameState.playMove(moves[i]);

            // incremental hash must equal a from-scratch recompute in the new position
            assertEquals(Zobrist.generateHash(bitboard, gameState.isWhiteTurn()), bitboard.getHash(),
                    "incremental hash diverged from full recompute after " + moveDesc);

            walk(gameState, depth - 1);

            bitboard.restoreState();
            gameState.switchPlayer();

            assertArrayEquals(piecesBefore, bitboard.getPieces(), "pieces mismatch after unmaking " + moveDesc);
            assertArrayEquals(occupanciesBefore, bitboard.getOccupancies(), "occupancies mismatch after unmaking " + moveDesc);
            assertEquals(castlesBefore, bitboard.getCastlesFlags(), "castle flags mismatch after unmaking " + moveDesc);
            assertEquals(enPassantBefore, bitboard.getEnPassantSquare(), "en passant mismatch after unmaking " + moveDesc);
            assertEquals(hashBefore, bitboard.getHash(), "hash mismatch after unmaking " + moveDesc);
        }
    }
}
