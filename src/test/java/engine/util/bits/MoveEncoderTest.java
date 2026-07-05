package engine.util.bits;

import engine.core.entity.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveEncoderTest {

    @Test
    void roundTrip_allFieldsDistinctAndSet() {
        int move = MoveEncoder.encodeMove(63, 0, Piece.BLACK_KING, Piece.BLACK_QUEEN, 1, 1, 1, 1);

        assertEquals(63, MoveEncoder.extractFrom(move));
        assertEquals(0, MoveEncoder.extractTo(move));
        assertEquals(Piece.BLACK_KING, MoveEncoder.extractPiece(move));
        assertEquals(Piece.BLACK_QUEEN, MoveEncoder.extractPromotionPiece(move));
        assertEquals(1, MoveEncoder.extractDoublePushFlag(move));
        assertEquals(1, MoveEncoder.extractCastlesFlag(move));
        assertEquals(1, MoveEncoder.extractEnPassantFlag(move));
        assertEquals(1, MoveEncoder.extractCaptureFlag(move));
        assertTrue(MoveEncoder.isMoveCapture(move));
    }

    @Test
    void roundTrip_allFlagsZero() {
        int move = MoveEncoder.encodeMove(12, 28, Piece.WHITE_PAWN, 0, 0, 0, 0, 0);

        assertEquals(12, MoveEncoder.extractFrom(move));
        assertEquals(28, MoveEncoder.extractTo(move));
        assertEquals(Piece.WHITE_PAWN, MoveEncoder.extractPiece(move));
        assertEquals(0, MoveEncoder.extractPromotionPiece(move));
        assertEquals(0, MoveEncoder.extractDoublePushFlag(move));
        assertEquals(0, MoveEncoder.extractCastlesFlag(move));
        assertEquals(0, MoveEncoder.extractEnPassantFlag(move));
        assertEquals(0, MoveEncoder.extractCaptureFlag(move));
        assertFalse(MoveEncoder.isMoveCapture(move));
    }

    @Test
    void fromAndTo_coverFullBoard_noCrossTalk() {
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to += 13) { // sample to keep test fast
                int move = MoveEncoder.encodeMove(from, to, Piece.WHITE_KNIGHT, 0, 0, 0, 0, 0);
                assertEquals(from, MoveEncoder.extractFrom(move), "from=" + from + " to=" + to);
                assertEquals(to, MoveEncoder.extractTo(move), "from=" + from + " to=" + to);
            }
        }
    }

    @Test
    void eachFlag_setIndependently_doesNotAffectOtherFlags() {
        int base = MoveEncoder.encodeMove(4, 20, Piece.WHITE_ROOK, 0, 0, 0, 0, 0);

        int withDoublePush = MoveEncoder.encodeMove(4, 20, Piece.WHITE_ROOK, 0, 1, 0, 0, 0);
        assertEquals(1, MoveEncoder.extractDoublePushFlag(withDoublePush));
        assertEquals(0, MoveEncoder.extractCastlesFlag(withDoublePush));
        assertEquals(0, MoveEncoder.extractEnPassantFlag(withDoublePush));
        assertEquals(0, MoveEncoder.extractCaptureFlag(withDoublePush));

        int withCastles = MoveEncoder.encodeMove(4, 20, Piece.WHITE_ROOK, 0, 0, 1, 0, 0);
        assertEquals(0, MoveEncoder.extractDoublePushFlag(withCastles));
        assertEquals(1, MoveEncoder.extractCastlesFlag(withCastles));
        assertEquals(0, MoveEncoder.extractEnPassantFlag(withCastles));
        assertEquals(0, MoveEncoder.extractCaptureFlag(withCastles));

        int withEnPassant = MoveEncoder.encodeMove(4, 20, Piece.WHITE_ROOK, 0, 0, 0, 1, 0);
        assertEquals(0, MoveEncoder.extractDoublePushFlag(withEnPassant));
        assertEquals(0, MoveEncoder.extractCastlesFlag(withEnPassant));
        assertEquals(1, MoveEncoder.extractEnPassantFlag(withEnPassant));
        assertEquals(0, MoveEncoder.extractCaptureFlag(withEnPassant));

        int withCapture = MoveEncoder.encodeMove(4, 20, Piece.WHITE_ROOK, 0, 0, 0, 0, 1);
        assertEquals(0, MoveEncoder.extractDoublePushFlag(withCapture));
        assertEquals(0, MoveEncoder.extractCastlesFlag(withCapture));
        assertEquals(0, MoveEncoder.extractEnPassantFlag(withCapture));
        assertEquals(1, MoveEncoder.extractCaptureFlag(withCapture));
        assertTrue(MoveEncoder.isMoveCapture(withCapture));

        // base itself should still decode cleanly after all this
        assertEquals(4, MoveEncoder.extractFrom(base));
        assertEquals(20, MoveEncoder.extractTo(base));
    }

    @Test
    void allPieceTypes_roundTripAsPieceAndAsPromotionPiece() {
        for (int piece = 0; piece < Piece.PIECE_TYPES; piece++) {
            int move = MoveEncoder.encodeMove(8, 16, piece, piece, 0, 0, 0, 0);
            assertEquals(piece, MoveEncoder.extractPiece(move), "piece " + piece);
            assertEquals(piece, MoveEncoder.extractPromotionPiece(move), "promotionPiece " + piece);
        }
    }

    @Test
    void isMoveCapture_falseWhenCaptureFlagUnset_evenWithOtherFlagsSet() {
        int move = MoveEncoder.encodeMove(4, 20, Piece.WHITE_QUEEN, Piece.WHITE_QUEEN, 1, 1, 1, 0);
        assertFalse(MoveEncoder.isMoveCapture(move));
    }
}
