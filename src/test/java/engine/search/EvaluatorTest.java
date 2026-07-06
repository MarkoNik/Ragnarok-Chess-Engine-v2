package engine.search;

import app.Constants;
import engine.core.state.GameState;
import engine.util.bits.FenParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A static evaluator that only looks at material + piece-square tables must be
 * symmetric: mirroring a position (flip ranks, swap colors) and negating the
 * side that "owns" each square should exactly negate the evaluation. If it
 * doesn't, White and Black are being scored by different rules.
 */
class EvaluatorTest {
    private static final Evaluator EVALUATOR = new Evaluator();

    // Single extra piece besides the two kings, placed off-center so any
    // piece-square table lookup produces a non-zero, non-trivial value.
    private static final String LONE_WHITE_PAWN = "4k3/8/8/8/4P3/8/8/4K3 w - - 0 1";
    private static final String LONE_WHITE_KNIGHT = "4k3/8/8/8/3N4/8/8/4K3 w - - 0 1";
    private static final String LONE_WHITE_BISHOP = "4k3/8/8/8/2B5/8/8/4K3 w - - 0 1";
    private static final String LONE_WHITE_ROOK = "4k3/8/8/8/8/8/8/3RK3 w - - 0 1";
    private static final String LONE_WHITE_QUEEN = "4k3/8/8/8/8/8/8/3QK3 w - - 0 1";

    private static String mirrorFen(String fen) {
        String[] parts = fen.split(" ");
        String[] ranks = parts[0].split("/");

        StringBuilder mirroredPlacement = new StringBuilder();
        for (int i = ranks.length - 1; i >= 0; i--) {
            for (char c : ranks[i].toCharArray()) {
                if (Character.isDigit(c)) {
                    mirroredPlacement.append(c);
                } else if (Character.isUpperCase(c)) {
                    mirroredPlacement.append(Character.toLowerCase(c));
                } else {
                    mirroredPlacement.append(Character.toUpperCase(c));
                }
            }
            if (i > 0) {
                mirroredPlacement.append('/');
            }
        }

        String activeColor = parts[1].equals("w") ? "b" : "w";

        String castling = parts[2];
        StringBuilder mirroredCastling = new StringBuilder();
        if (castling.equals("-")) {
            mirroredCastling.append('-');
        } else {
            for (char c : castling.toCharArray()) {
                mirroredCastling.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
            }
        }

        String enPassant = parts[3];
        String mirroredEnPassant;
        if (enPassant.equals("-")) {
            mirroredEnPassant = "-";
        } else {
            char file = enPassant.charAt(0);
            int rank = Character.getNumericValue(enPassant.charAt(1));
            mirroredEnPassant = "" + file + (9 - rank);
        }

        String halfmove = parts.length > 4 ? parts[4] : "0";
        String fullmove = parts.length > 5 ? parts[5] : "1";

        return mirroredPlacement + " " + activeColor + " " + mirroredCastling + " " + mirroredEnPassant
                + " " + halfmove + " " + fullmove;
    }

    @Test
    void mirrorFen_startingPosition_isSelfSymmetricPlacement() {
        // Sanity check on the test helper itself: the starting position's piece
        // placement is symmetric under (flip ranks + swap colors), so this must
        // round-trip to the same placement string.
        String mirroredPlacement = mirrorFen(Constants.INITIAL_FEN).split(" ")[0];
        String originalPlacement = Constants.INITIAL_FEN.split(" ")[0];
        assertEquals(originalPlacement, mirroredPlacement);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            Constants.INITIAL_FEN,
            LONE_WHITE_PAWN,
            LONE_WHITE_KNIGHT,
            LONE_WHITE_BISHOP,
            LONE_WHITE_ROOK,
            LONE_WHITE_QUEEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", // kiwipete
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
    })
    void evaluatePieces_isSymmetricUnderMirroring(String fen) {
        int eval = EVALUATOR.evaluatePieces(FenParser.parseFEN(fen).getBitboard());
        int mirroredEval = EVALUATOR.evaluatePieces(FenParser.parseFEN(mirrorFen(fen)).getBitboard());
        assertEquals(eval, -mirroredEval, "material-only eval should negate exactly under mirroring: " + fen);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            Constants.INITIAL_FEN,
            LONE_WHITE_PAWN,
            LONE_WHITE_KNIGHT,
            LONE_WHITE_BISHOP,
            LONE_WHITE_ROOK,
            LONE_WHITE_QUEEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", // kiwipete
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
    })
    void evaluateAdvanced_isSymmetricUnderMirroring(String fen) {
        GameState gameState = FenParser.parseFEN(fen);
        GameState mirroredGameState = FenParser.parseFEN(mirrorFen(fen));

        int eval = EVALUATOR.evaluateAdvanced(gameState.getBitboard());
        int mirroredEval = EVALUATOR.evaluateAdvanced(mirroredGameState.getBitboard());

        assertEquals(eval, -mirroredEval,
                "PST-aware eval should negate exactly under mirroring: " + fen
                        + " (eval=" + eval + ", mirroredEval=" + mirroredEval + ")");
    }
}
