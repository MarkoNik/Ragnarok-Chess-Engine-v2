package engine.core.state;

import app.EngineLogger;
import engine.core.entity.Piece;
import engine.core.entity.UciMove;
import engine.util.Zobrist;
import engine.util.bits.BitUtils;
import engine.util.bits.MoveEncoder;

import java.util.Arrays;

import static app.Constants.*;
import static engine.core.entity.Piece.*;

public class Bitboard {
    private long[] pieces = new long[PIECE_TYPES];
    private long[] occupancies = new long[OCCUPANCY_TYPES];

    /**
     * 0001 - white kingside castles<br>
     * 0010 - white queenside castles<br>
     * 0100 - black kingside castles<br>
     * 1000 - black queenside castles<br>
     */
    private byte castlesFlags = 0;
    private int enPassantSquare = 0;
    private long hash;

    private static final int BACKUP_STACK_SIZE = 100000;

    private final long[][] piecesBackup = new long[BACKUP_STACK_SIZE][PIECE_TYPES];
    private final long[][] occupanciesBackup = new long[BACKUP_STACK_SIZE][OCCUPANCY_TYPES];
    private final byte[] castlesFlagsBackup = new byte[BACKUP_STACK_SIZE];
    private final int[] enPassantSquareBackup = new int[BACKUP_STACK_SIZE];
    private final long[] hashBackup = new long[BACKUP_STACK_SIZE];
    private int backupStackPointer = 0;

    /**
     * This function is used to set the bit
     * corresponding to the given square for the given piece.
     * @param square
     * @param piece
     */
    public void setPiece(int square, char piece) {
        // set the piece bitboard
        long bitboard = 1L << square;
        pieces[pieceMap.get(piece)] |= bitboard;

        // set the occupancies
        if (pieceMap.get(piece) < WHITE_PIECE_TYPES) {
            occupancies[WHITE] |= bitboard;
        }
        else {
            occupancies[BLACK] |= bitboard;
        }
        occupancies[BOTH] |= bitboard;
    }

    /**
     * Play a move on the board and update the state
     * @param move
     * @param isWhiteTurn
     * @param capturesOnly
     */
    public void makeMove(int move, boolean isWhiteTurn, boolean capturesOnly) {
        if (!capturesOnly) {
            int from = MoveEncoder.extractFrom(move);
            int to = MoveEncoder.extractTo(move);
            int piece = MoveEncoder.extractPiece(move);
            int promotionPiece = MoveEncoder.extractPromotionPiece(move);
            int doublePushFlag = MoveEncoder.extractDoublePushFlag(move);
            int castlesFlag = MoveEncoder.extractCastlesFlag(move);
            int enPassantFlag = MoveEncoder.extractEnPassantFlag(move);
            int captureFlag = MoveEncoder.extractCaptureFlag(move);
            pieces[piece] = BitUtils.popBit(pieces[piece], from);
            pieces[piece] = BitUtils.setBit(pieces[piece], to);

            hash ^= Zobrist.pieceKeys[piece][from];
            hash ^= Zobrist.pieceKeys[piece][to];

            if (isWhiteTurn) {
                occupancies[WHITE] = BitUtils.popBit(occupancies[WHITE], from);
                occupancies[WHITE] = BitUtils.setBit(occupancies[WHITE], to);
                if (castlesFlags != 0) {
                    hash ^= Zobrist.castlesFlagsKeys[castlesFlags];
                    if (from == WHITE_KINGSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1110;
                    }
                    if (from == WHITE_QUEENSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1101;
                    }
                    if (to == BLACK_KINGSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1011;
                    }
                    if (to == BLACK_QUEENSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b0111;
                    }
                    if (from == WHITE_KING_POSITION) {
                        castlesFlags &= 0b1100;
                    }
                    hash ^= Zobrist.castlesFlagsKeys[castlesFlags];
                }
            }
            else {
                occupancies[BLACK] = BitUtils.popBit(occupancies[BLACK], from);
                occupancies[BLACK] = BitUtils.setBit(occupancies[BLACK], to);
                if (castlesFlags != 0) {
                    hash ^= Zobrist.castlesFlagsKeys[castlesFlags];
                    if (from == BLACK_KINGSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1011;
                    }
                    if (from == BLACK_QUEENSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b0111;
                    }
                    if (to == WHITE_KINGSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1110;
                    }
                    if (to == WHITE_QUEENSIDE_ROOK_POSITION) {
                        castlesFlags &= 0b1101;
                    }
                    if (from == BLACK_KING_POSITION) {
                        castlesFlags &= 0b0011;
                    }
                    hash ^= Zobrist.castlesFlagsKeys[castlesFlags];
                }
            }

            if (captureFlag != 0) {
                if (isWhiteTurn) {
                    // iterate over all black piece types
                    for (int i = WHITE_PIECE_TYPES; i < PIECE_TYPES; i++) {
                        if (BitUtils.getBit(pieces[i], to)) {
                            pieces[i] = BitUtils.popBit(pieces[i], to);
                            hash ^= Zobrist.pieceKeys[i][to];
                            break;
                        }
                    }
                    occupancies[BLACK] = BitUtils.popBit(occupancies[BLACK], to);
                }
                else {
                    // iterate over all white piece types
                    for (int i = 0; i < WHITE_PIECE_TYPES; i++) {
                        if (BitUtils.getBit(pieces[i], to)) {
                            pieces[i] = BitUtils.popBit(pieces[i], to);
                            hash ^= Zobrist.pieceKeys[i][to];
                            break;
                        }
                    }
                    occupancies[WHITE] = BitUtils.popBit(occupancies[WHITE], to);
                }
            }

            if (promotionPiece != 0) {
                pieces[piece] = BitUtils.popBit(pieces[piece], to);
                pieces[promotionPiece] = BitUtils.setBit(pieces[promotionPiece], to);
                hash ^= Zobrist.pieceKeys[piece][to];
                hash ^= Zobrist.pieceKeys[promotionPiece][to];
            }

            if (castlesFlag != 0) {
                if (to == WHITE_KINGSIDE_CASTLES_SQUARE) {
                    pieces[WHITE_ROOK] = BitUtils.popBit(pieces[WHITE_ROOK], WHITE_KINGSIDE_ROOK_POSITION);
                    pieces[WHITE_ROOK] = BitUtils.setBit(pieces[WHITE_ROOK], WHITE_KINGSIDE_CASTLES_SQUARE - 1);
                    occupancies[WHITE] = BitUtils.popBit(occupancies[WHITE], WHITE_KINGSIDE_ROOK_POSITION);
                    occupancies[WHITE] = BitUtils.setBit(occupancies[WHITE], WHITE_KINGSIDE_CASTLES_SQUARE - 1);
                    hash ^= Zobrist.pieceKeys[WHITE_ROOK][WHITE_KINGSIDE_ROOK_POSITION];
                    hash ^= Zobrist.pieceKeys[WHITE_ROOK][WHITE_KINGSIDE_CASTLES_SQUARE - 1];
                }
                if (to == WHITE_QUEENSIDE_CASTLES_SQUARE) {
                    pieces[WHITE_ROOK] = BitUtils.popBit(pieces[WHITE_ROOK], WHITE_QUEENSIDE_ROOK_POSITION);
                    pieces[WHITE_ROOK] = BitUtils.setBit(pieces[WHITE_ROOK], WHITE_QUEENSIDE_CASTLES_SQUARE + 1);
                    occupancies[WHITE] = BitUtils.popBit(occupancies[WHITE], WHITE_QUEENSIDE_ROOK_POSITION);
                    occupancies[WHITE] = BitUtils.setBit(occupancies[WHITE], WHITE_QUEENSIDE_CASTLES_SQUARE + 1);
                    hash ^= Zobrist.pieceKeys[WHITE_ROOK][WHITE_QUEENSIDE_ROOK_POSITION];
                    hash ^= Zobrist.pieceKeys[WHITE_ROOK][WHITE_QUEENSIDE_CASTLES_SQUARE + 1];
                }
                if (to == BLACK_KINGSIDE_CASTLES_SQUARE) {
                    pieces[BLACK_ROOK] = BitUtils.popBit(pieces[BLACK_ROOK], BLACK_KINGSIDE_ROOK_POSITION);
                    pieces[BLACK_ROOK] = BitUtils.setBit(pieces[BLACK_ROOK], BLACK_KINGSIDE_CASTLES_SQUARE - 1);
                    occupancies[BLACK] = BitUtils.popBit(occupancies[BLACK], BLACK_KINGSIDE_ROOK_POSITION);
                    occupancies[BLACK] = BitUtils.setBit(occupancies[BLACK], BLACK_KINGSIDE_CASTLES_SQUARE - 1);
                    hash ^= Zobrist.pieceKeys[BLACK_ROOK][BLACK_KINGSIDE_ROOK_POSITION];
                    hash ^= Zobrist.pieceKeys[BLACK_ROOK][BLACK_KINGSIDE_CASTLES_SQUARE - 1];
                }
                if (to == BLACK_QUEENSIDE_CASTLES_SQUARE) {
                    pieces[BLACK_ROOK] = BitUtils.popBit(pieces[BLACK_ROOK], BLACK_QUEENSIDE_ROOK_POSITION);
                    pieces[BLACK_ROOK] = BitUtils.setBit(pieces[BLACK_ROOK], BLACK_QUEENSIDE_CASTLES_SQUARE + 1);
                    occupancies[BLACK] = BitUtils.popBit(occupancies[BLACK], BLACK_QUEENSIDE_ROOK_POSITION);
                    occupancies[BLACK] = BitUtils.setBit(occupancies[BLACK], BLACK_QUEENSIDE_CASTLES_SQUARE + 1);
                    hash ^= Zobrist.pieceKeys[BLACK_ROOK][BLACK_QUEENSIDE_ROOK_POSITION];
                    hash ^= Zobrist.pieceKeys[BLACK_ROOK][BLACK_QUEENSIDE_CASTLES_SQUARE + 1];
                }
            }

            if (enPassantFlag != 0) {
                if (isWhiteTurn) {
                    pieces[BLACK_PAWN] = BitUtils.popBit(pieces[BLACK_PAWN], enPassantSquare);
                    occupancies[BLACK] = BitUtils.popBit(occupancies[BLACK], enPassantSquare);
                    hash ^= Zobrist.pieceKeys[BLACK_PAWN][enPassantSquare];
                }
                else {
                    pieces[WHITE_PAWN] = BitUtils.popBit(pieces[WHITE_PAWN], enPassantSquare);
                    occupancies[WHITE] = BitUtils.popBit(occupancies[WHITE], enPassantSquare);
                    hash ^= Zobrist.pieceKeys[WHITE_PAWN][enPassantSquare];
                }
            }

            if (doublePushFlag != 0) {
                if (enPassantSquare != -1) {
                    hash ^= Zobrist.enPassantKeys[enPassantSquare];
                }
                enPassantSquare = to;
                hash ^= Zobrist.enPassantKeys[enPassantSquare];
            } else {
                if (enPassantSquare != -1) {
                    hash ^= Zobrist.enPassantKeys[enPassantSquare];
                    enPassantSquare = -1;
                }
            }

            occupancies[BOTH] = occupancies[WHITE] | occupancies[BLACK];
            hash ^= Zobrist.sidesKey;

//            // check if incremental Zobrist hash is correct
//            long fullHash = Zobrist.generateHash(this, !isWhiteTurn);
//            if (hash != fullHash) {
//                logBoardState();
//                MoveEncoder.logMove(move);
//                EngineLogger.error("Incremental hash: " + hash + " should be: " + fullHash);
//                Zobrist.logError(hash, fullHash);
//            }
        }
        else {
            // ensure only capture moves are made
            if (MoveEncoder.isMoveCapture(move)) {
                makeMove(move, isWhiteTurn, false);
            }
        }
    }

    public void makeUciMove(UciMove uciMove, boolean isWhiteTurn) {
        int from = uciMove.from;
        int to = uciMove.to;
        int piece = -1;
        for (int i = 0; i < PIECE_TYPES; i++) {
            if ((pieces[i] & (1L << from)) != 0) {
                piece = i;
                break;
            }
        }

        int promotionPiece = uciMove.promotionPiece;
        if (isWhiteTurn) {
            if (promotionPiece == Piece.QUEEN) promotionPiece = Piece.WHITE_QUEEN;
            else if (promotionPiece == Piece.ROOK) promotionPiece = WHITE_ROOK;
            else if (promotionPiece == Piece.BISHOP) promotionPiece = Piece.WHITE_BISHOP;
            else if (promotionPiece == Piece.KNIGHT) promotionPiece = Piece.WHITE_KNIGHT;
        }
        else {
            if (promotionPiece == Piece.QUEEN) promotionPiece = Piece.BLACK_QUEEN;
            else if (promotionPiece == Piece.ROOK) promotionPiece = Piece.BLACK_ROOK;
            else if (promotionPiece == Piece.BISHOP) promotionPiece = Piece.BLACK_BISHOP;
            else if (promotionPiece == Piece.KNIGHT) promotionPiece = Piece.BLACK_KNIGHT;
        }

        int doublePushFlag = 0;
        if (uciMove.potentialDoublePush == 1 && isWhiteTurn && piece == Piece.WHITE_PAWN
                || uciMove.potentialDoublePush == 1 && !isWhiteTurn && piece == Piece.BLACK_PAWN) {
            doublePushFlag = 1;
        }

        int enPassantFlag = 0;
        if (enPassantSquare != -1) {
            if ((isWhiteTurn && piece == Piece.WHITE_PAWN && to == enPassantSquare - 8)
                    || (!isWhiteTurn && piece == Piece.BLACK_PAWN && to == enPassantSquare + 8)) {
                enPassantFlag = 1;
            }
        }

        int castlesFlag = 0;
        if ((uciMove.potentialCastlesFlag == 1 && isWhiteTurn && piece == Piece.WHITE_KING)
            || (uciMove.potentialCastlesFlag == 1 && !isWhiteTurn && piece == Piece.BLACK_KING)) {
            castlesFlag = 1;
        }

        int captureFlag = 0;
        if ((isWhiteTurn && (occupancies[BLACK] & (1L << to)) != 0)
                || (!isWhiteTurn && (occupancies[WHITE] & (1L << to)) != 0)
                || enPassantFlag == 1) {
            captureFlag = 1;
        }

        int move = MoveEncoder.encodeMove(from, to, piece, promotionPiece, doublePushFlag, castlesFlag, enPassantFlag, captureFlag);
        makeMove(move, isWhiteTurn, false);
    }

    /**
     * Passes the turn without making a real move (used for null-move pruning).
     * Only touches what actually changes when a side "passes": en passant
     * rights lapse the same as after any real move, and the side-to-move hash
     * term flips. Pair with backupState()/restoreState() same as makeMove().
     */
    public void makeNullMove() {
        if (enPassantSquare != -1) {
            hash ^= Zobrist.enPassantKeys[enPassantSquare];
            enPassantSquare = -1;
        }
        hash ^= Zobrist.sidesKey;
    }

    public void backupState() {
        if (backupStackPointer >= BACKUP_STACK_SIZE) {
            throw new IllegalStateException("Bitboard backup stack overflow: backupState() was called "
                    + BACKUP_STACK_SIZE + " times without a matching restoreState(). This means some "
                    + "caller is backing up state without restoring it, e.g. an unbounded loop.");
        }
        piecesBackup[backupStackPointer] = pieces.clone();
        occupanciesBackup[backupStackPointer] = occupancies.clone();
        castlesFlagsBackup[backupStackPointer] = castlesFlags;
        enPassantSquareBackup[backupStackPointer] = enPassantSquare;
        hashBackup[backupStackPointer] = hash;
        backupStackPointer++;
    }

    public void restoreState() {
        if (backupStackPointer <= 0) {
            throw new IllegalStateException("Bitboard backup stack underflow: restoreState() was called "
                    + "without a matching prior backupState().");
        }
        backupStackPointer--;
        pieces = piecesBackup[backupStackPointer];
        occupancies = occupanciesBackup[backupStackPointer];
        castlesFlags = castlesFlagsBackup[backupStackPointer];
        enPassantSquare = enPassantSquareBackup[backupStackPointer];
        hash = hashBackup[backupStackPointer];
    }

    /**
     * Function used for outputting the board state.
     * The function aggregates each individual piece bitboard.
     */
    public void logBoardState() {
        char[] output = new char[BOARD_SIZE];
        Arrays.fill(output, ' ');
        for (int i = 0; i < PIECE_TYPES; i++) {
            long tempBitboard = pieces[i];
            while (tempBitboard != 0) {
                int square = BitUtils.getLs1bIndex(tempBitboard);
                tempBitboard = BitUtils.popBit(tempBitboard, square);
                output[square] = asciiPieces[i];
            }
        }

        StringBuilder sb = new StringBuilder("\n\n");
        for (int i = 0; i < RANKS; i++) {
            for (int j = 0; j < FILES; j++) {
                if (j == 0) sb.append("   ").append(8 - i).append("   ");
                sb.append(output[FILES * i + j] == ' ' ? '.' : output[FILES * i + j]).append(" ");
            }
            sb.append("\n");
        }
        sb.append("\n       a b c d e f g h\n");
        sb.append("\nHash: ").append(hash);
        sb.append("\nCastles flags: ").append(castlesFlags);
        sb.append("\nEn passant square: ").append(enPassantSquare).append("\n");
        EngineLogger.debug(sb.toString());
//        logBitboards();
    }

    public void logBitboards() {
        for (int i = 0; i < PIECE_TYPES; i++) {
            EngineLogger.debug("Piece bitboard: " + Piece.pieceCodeToPiece[i]);
            BitUtils.logBitboard(pieces[i]);
        }
    }

    public void generateHash(boolean isWhiteTurn) {
        hash = Zobrist.generateHash(this, isWhiteTurn);
    }

    public long getHash() {
        return hash;
    }

    public long[] getPieces() {
        return pieces;
    }

    public long[] getOccupancies() {
        return occupancies;
    }

    public byte getCastlesFlags() {
        return castlesFlags;
    }

    public int getEnPassantSquare() {
        return enPassantSquare;
    }

    public void setCastlesFlags(byte castlesFlags) {
        this.castlesFlags = castlesFlags;
    }

    public void setEnPassantSquare(int enPassantSquare) {
        this.enPassantSquare = enPassantSquare;
    }
}
