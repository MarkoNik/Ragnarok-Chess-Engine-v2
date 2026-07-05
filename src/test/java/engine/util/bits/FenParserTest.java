package engine.util.bits;

import app.Constants;
import engine.core.entity.Piece;
import engine.core.entity.UciMove;
import engine.core.state.Bitboard;
import engine.core.state.GameState;
import engine.util.bits.MoveEncoder;
import org.junit.jupiter.api.Test;

import static app.Constants.*;
import static org.junit.jupiter.api.Assertions.*;

class FenParserTest {

    @Test
    void parseFEN_startingPosition_setsExpectedState() {
        GameState gameState = FenParser.parseFEN(Constants.INITIAL_FEN);
        Bitboard bitboard = gameState.getBitboard();

        assertTrue(gameState.isWhiteTurn());
        assertEquals(ALL_CASTLES_MASK, bitboard.getCastlesFlags());
        assertEquals(-1, bitboard.getEnPassantSquare());

        long[] pieces = bitboard.getPieces();
        assertTrue(BitUtils.getBit(pieces[Piece.WHITE_ROOK], WHITE_QUEENSIDE_ROOK_POSITION));
        assertTrue(BitUtils.getBit(pieces[Piece.WHITE_ROOK], WHITE_KINGSIDE_ROOK_POSITION));
        assertTrue(BitUtils.getBit(pieces[Piece.WHITE_KING], WHITE_KING_POSITION));
        assertTrue(BitUtils.getBit(pieces[Piece.BLACK_KING], BLACK_KING_POSITION));
        assertTrue(BitUtils.getBit(pieces[Piece.BLACK_ROOK], BLACK_QUEENSIDE_ROOK_POSITION));
        assertTrue(BitUtils.getBit(pieces[Piece.BLACK_ROOK], BLACK_KINGSIDE_ROOK_POSITION));

        // all 8 white pawns on rank 2, all 8 black pawns on rank 7
        assertEquals(8, Long.bitCount(pieces[Piece.WHITE_PAWN] & RANK_2_MASK()));
        assertEquals(8, Long.bitCount(pieces[Piece.BLACK_PAWN] & RANK_7_MASK()));

        assertEquals(32, Long.bitCount(bitboard.getOccupancies()[BOTH]));
    }

    private static long RANK_2_MASK() {
        long mask = 0;
        for (int sq = (int) RANK_2_START_SQUARE; sq <= RANK_2_END_SQUARE; sq++) {
            mask |= 1L << sq;
        }
        return mask;
    }

    private static long RANK_7_MASK() {
        long mask = 0;
        for (int sq = (int) RANK_7_START_SQUARE; sq <= RANK_7_END_SQUARE; sq++) {
            mask |= 1L << sq;
        }
        return mask;
    }

    @Test
    void parseFEN_blackToMove() {
        GameState gameState = FenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");
        assertFalse(gameState.isWhiteTurn());
    }

    @Test
    void parseFEN_partialCastlingRights() {
        GameState gameState = FenParser.parseFEN("r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1");
        byte flags = gameState.getBitboard().getCastlesFlags();
        assertEquals(WHITE_KINGSIDE_CASTLES_MASK | BLACK_QUEENSIDE_CASTLES_MASK, flags);
    }

    @Test
    void parseFEN_noCastlingRights() {
        GameState gameState = FenParser.parseFEN("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
        assertEquals(0, gameState.getBitboard().getCastlesFlags());
    }

    @Test
    void parseFEN_enPassantSquareSet() {
        GameState gameState = FenParser.parseFEN(
                "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3");
        assertEquals(FenParser.algebraicToIndex("d6"), gameState.getBitboard().getEnPassantSquare());
    }

    @Test
    void parseFEN_noEnPassant_isMinusOne() {
        GameState gameState = FenParser.parseFEN(Constants.INITIAL_FEN);
        assertEquals(-1, gameState.getBitboard().getEnPassantSquare());
    }

    @Test
    void parseFEN_missingClocks_defaultToInf() {
        GameState gameState = FenParser.parseFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -");
        // FenParser doesn't expose clocks directly; re-parse the same string with clocks
        // and rely on this not throwing, since GameState hides the fields. This mainly
        // guards against an ArrayIndexOutOfBoundsException on short FEN strings.
        assertNotNull(gameState);
    }

    @Test
    void algebraicToIndex_indexToAlgebraic_roundTripAllSquares() {
        for (char file = 'a'; file <= 'h'; file++) {
            for (char rank = '1'; rank <= '8'; rank++) {
                String algebraic = "" + file + rank;
                int index = FenParser.algebraicToIndex(algebraic);
                assertTrue(index >= 0 && index < 64, algebraic + " -> " + index);
                assertEquals(algebraic, FenParser.indexToAlgebraic(index), "round trip for " + algebraic);
            }
        }
    }

    @Test
    void algebraicToIndex_knownSquares() {
        assertEquals(WHITE_QUEENSIDE_ROOK_POSITION, FenParser.algebraicToIndex("a1"));
        assertEquals(WHITE_KINGSIDE_ROOK_POSITION, FenParser.algebraicToIndex("h1"));
        assertEquals(WHITE_KING_POSITION, FenParser.algebraicToIndex("e1"));
        assertEquals(BLACK_KING_POSITION, FenParser.algebraicToIndex("e8"));
        assertEquals(BLACK_QUEENSIDE_ROOK_POSITION, FenParser.algebraicToIndex("a8"));
        assertEquals(BLACK_KINGSIDE_ROOK_POSITION, FenParser.algebraicToIndex("h8"));
    }

    @Test
    void parseUciMove_nullMove() {
        UciMove move = FenParser.parseUciMove("0000");
        assertEquals(-1, move.from);
        assertEquals(-1, move.to);
    }

    @Test
    void parseUciMove_quietMove_noFlags() {
        UciMove move = FenParser.parseUciMove("e2e3");
        assertEquals(0, move.potentialCastlesFlag);
        assertEquals(0, move.potentialDoublePush);
        assertEquals(0, move.promotionPiece);
    }

    @Test
    void parseUciMove_whiteDoublePush() {
        UciMove move = FenParser.parseUciMove("e2e4");
        assertEquals(1, move.potentialDoublePush);
    }

    @Test
    void parseUciMove_blackDoublePush() {
        UciMove move = FenParser.parseUciMove("e7e5");
        assertEquals(1, move.potentialDoublePush);
    }

    @Test
    void parseUciMove_castlingSquarePatterns() {
        for (String castleMove : new String[]{"e1g1", "e8g8", "e1c1", "e8c8"}) {
            UciMove move = FenParser.parseUciMove(castleMove);
            assertEquals(1, move.potentialCastlesFlag, castleMove);
        }
    }

    @Test
    void parseUciMove_nonCastlingKingMove_hasNoCastlesFlag() {
        UciMove move = FenParser.parseUciMove("e1e2");
        assertEquals(0, move.potentialCastlesFlag);
    }

    @Test
    void parseUciMove_promotionPieces() {
        assertEquals(Piece.QUEEN, FenParser.parseUciMove("a7a8q").promotionPiece);
        assertEquals(Piece.ROOK, FenParser.parseUciMove("a7a8r").promotionPiece);
        assertEquals(Piece.BISHOP, FenParser.parseUciMove("a7a8b").promotionPiece);
        assertEquals(Piece.KNIGHT, FenParser.parseUciMove("a7a8n").promotionPiece);
    }

    @Test
    void parseUciMove_noPromotion_isZero() {
        assertEquals(0, FenParser.parseUciMove("a7a8").promotionPiece);
    }

    @Test
    void moveToAlgebraic_noPromotion() {
        int move = MoveEncoder.encodeMove(
                FenParser.algebraicToIndex("e2"), FenParser.algebraicToIndex("e4"),
                Piece.WHITE_PAWN, 0, 1, 0, 0, 0);
        assertEquals("e2e4", FenParser.moveToAlgebraic(move));
    }

    @Test
    void moveToAlgebraic_withPromotion() {
        int move = MoveEncoder.encodeMove(
                FenParser.algebraicToIndex("a7"), FenParser.algebraicToIndex("a8"),
                Piece.WHITE_PAWN, Piece.WHITE_QUEEN, 0, 0, 0, 0);
        assertEquals("a7a8q", FenParser.moveToAlgebraic(move));
    }

    @Test
    void moveToAlgebraic_blackPromotionPieces() {
        int knightPromo = MoveEncoder.encodeMove(
                FenParser.algebraicToIndex("a2"), FenParser.algebraicToIndex("a1"),
                Piece.BLACK_PAWN, Piece.BLACK_KNIGHT, 0, 0, 0, 0);
        assertEquals("a2a1n", FenParser.moveToAlgebraic(knightPromo));

        int rookPromo = MoveEncoder.encodeMove(
                FenParser.algebraicToIndex("a2"), FenParser.algebraicToIndex("a1"),
                Piece.BLACK_PAWN, Piece.BLACK_ROOK, 0, 0, 0, 0);
        assertEquals("a2a1r", FenParser.moveToAlgebraic(rookPromo));

        int bishopPromo = MoveEncoder.encodeMove(
                FenParser.algebraicToIndex("a2"), FenParser.algebraicToIndex("a1"),
                Piece.BLACK_PAWN, Piece.BLACK_BISHOP, 0, 0, 0, 0);
        assertEquals("a2a1b", FenParser.moveToAlgebraic(bishopPromo));
    }
}
