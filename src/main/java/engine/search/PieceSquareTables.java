package engine.search;

import engine.core.entity.Piece;

public class PieceSquareTables {

    // Pawn piece-square table
    public static final int[] PAWN_TABLE = new int[]{
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 5, 5, -10, -10, 5, 5, 5,
            1, 1, 2, 5, 5, 2, 1, 1,
            0, 0, 0, 20, 20, 0, 0, 0,
            1, -1, -2, 0, 0, -2, -1, 1,
            1, 2, 2, -5, -5, 2, 2, 1,
            5, 5, 10, -10, -10, 10, 5, 5,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    // Knight piece-square table
    public static final int[] KNIGHT_TABLE = new int[]{
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
    };

    // Bishop piece-square table
    public static final int[] BISHOP_TABLE = new int[]{
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
    };

    // Rook piece-square table
    public static final int[] ROOK_TABLE = new int[]{
            0, 0, 5, 10, 10, 5, 0, 0,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            5, 10, 10, 10, 10, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
    };

    // Queen piece-square table
    public static final int[] QUEEN_TABLE = new int[]{
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20
    };

    // King middle-game piece-square table
    public static final int[] KING_MG_TABLE = new int[]{
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20
    };

    // King end-game piece-square table
    public static final int[] KING_EG_TABLE = new int[]{
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50
    };

    public static int evaluatePiece(int piece, int square, boolean isEndgame, boolean isWhitePiece) {
        if (!isWhitePiece) {
            // Flip the board for black pieces
            square = 63 - square;
        }

        // piece is a color+type index (WHITE_PAWN=0 .. BLACK_KING=11); reduce to a
        // color-agnostic type in [0,5] matching WHITE_PAWN..WHITE_KING's own values,
        // so the switch below doesn't need the separate PAWN..KING (1-6) constants
        // that caused every case here to be off by one relative to what's passed in.
        int pieceType = piece % Piece.WHITE_PIECE_TYPES;
        int result = switch (pieceType) {
            case Piece.WHITE_PAWN -> PAWN_TABLE[square];
            case Piece.WHITE_KNIGHT -> KNIGHT_TABLE[square];
            case Piece.WHITE_BISHOP -> BISHOP_TABLE[square];
            case Piece.WHITE_ROOK -> ROOK_TABLE[square];
            case Piece.WHITE_QUEEN -> QUEEN_TABLE[square];
            case Piece.WHITE_KING -> isEndgame ? KING_EG_TABLE[square] : KING_MG_TABLE[square];
            default -> 0;
        };
        // invert the result for black pieces
        return isWhitePiece ? result : -result;
    }
}

