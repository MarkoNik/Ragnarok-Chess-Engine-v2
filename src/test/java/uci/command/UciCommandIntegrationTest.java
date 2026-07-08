package uci.command;

import engine.core.bitboard.BitboardHelper;
import engine.core.entity.Piece;
import engine.core.state.Bitboard;
import engine.core.state.EngineState;
import engine.search.MoveGenerator;
import engine.util.bits.BitUtils;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static app.Constants.ALL_CASTLES_MASK;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives EngineState through the actual Command classes the CLI dispatches
 * to (PositionCommand, GoCommand, ...), rather than calling engine internals
 * directly - this is the same code path a real GUI or bench/run_match.py
 * exercises over UCI, minus the stdin/stdout plumbing itself.
 */
class UciCommandIntegrationTest {

    @Test
    void position_and_go_produceALegalMoveFromStartpos() {
        EngineState engineState = new EngineState();
        new PositionCommand("position startpos").execute(engineState);

        Set<String> legalFromStartpos = legalMovesAlgebraic(engineState.getGameState().getBitboard(), true);

        new GoCommand("go depth 3").execute(engineState);

        String bestMove = FenParser.moveToAlgebraic(engineState.getBestMove());
        assertTrue(legalFromStartpos.contains(bestMove),
                "engine chose " + bestMove + " which isn't a legal opening move");
    }

    @Test
    void position_withMoves_appliesThemToGameState() {
        EngineState engineState = new EngineState();
        new PositionCommand("position startpos moves e2e4 e7e5 g1f3").execute(engineState);

        Bitboard bitboard = engineState.getGameState().getBitboard();
        assertFalse(engineState.getGameState().isWhiteTurn(), "black to move after 3 half-moves");
        assertTrue(BitUtils.getBit(bitboard.getPieces()[Piece.WHITE_KNIGHT], FenParser.algebraicToIndex("f3")));
        assertFalse(BitUtils.getBit(bitboard.getPieces()[Piece.WHITE_KNIGHT], FenParser.algebraicToIndex("g1")));
        assertTrue(BitUtils.getBit(bitboard.getPieces()[Piece.WHITE_PAWN], FenParser.algebraicToIndex("e4")));
        assertTrue(BitUtils.getBit(bitboard.getPieces()[Piece.BLACK_PAWN], FenParser.algebraicToIndex("e5")));
    }

    @Test
    void position_withExplicitFen_setsThatPosition() {
        EngineState engineState = new EngineState();
        String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        new PositionCommand("position fen " + fen).execute(engineState);

        assertTrue(engineState.getGameState().isWhiteTurn());
        assertEquals(ALL_CASTLES_MASK, engineState.getGameState().getBitboard().getCastlesFlags());
    }

    @Test
    void position_fen_and_go_findsForcedMateThroughTheFullCommandStack() {
        EngineState engineState = new EngineState();
        new PositionCommand("position fen 6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1").execute(engineState);
        new GoCommand("go depth 4").execute(engineState);

        assertEquals("a1a8", FenParser.moveToAlgebraic(engineState.getBestMove()));
    }

    @Test
    void quitCommand_signalsLoopTermination() {
        assertEquals(-1, new QuitCommand().execute(new EngineState()));
    }

    @Test
    void isReadyCommand_doesNotSignalTermination() {
        assertEquals(0, new IsReadyCommand().execute(new EngineState()));
    }

    @Test
    void setOptionCommand_togglesEngineConfigFlag() {
        EngineState engineState = new EngineState();
        assertTrue(engineState.getConfig().isEnabled("NullMovePruning"));

        new SetOptionCommand("setoption name NullMovePruning value false").execute(engineState);

        assertFalse(engineState.getConfig().isEnabled("NullMovePruning"));
    }

    @Test
    void setOptionCommand_unknownOption_doesNotThrow() {
        EngineState engineState = new EngineState();
        // Real GUIs send options this engine doesn't support (e.g. "Hash", "Ponder");
        // that must be a no-op, not a crash that kills the whole UCI session.
        assertDoesNotThrow(() ->
                new SetOptionCommand("setoption name Hash value 128").execute(engineState));
    }

    private static Set<String> legalMovesAlgebraic(Bitboard bitboard, boolean isWhiteTurn) {
        MoveGenerator moveGenerator = new MoveGenerator(new BitboardHelper());
        moveGenerator.setBitboard(bitboard);
        int[] moves = moveGenerator.generateLegalMoves(isWhiteTurn).clone();
        int moveCount = moveGenerator.getMoveCounter();
        moveGenerator.clearMoves();

        Set<String> algebraic = new HashSet<>();
        for (int i = 0; i < moveCount; i++) {
            algebraic.add(FenParser.moveToAlgebraic(moves[i]));
        }
        return algebraic;
    }
}
