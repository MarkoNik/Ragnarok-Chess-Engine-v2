package engine.search;

import engine.core.entity.Piece;
import engine.core.state.Bitboard;
import engine.core.state.TranspositionTable;
import engine.util.bits.MoveEncoder;

import java.util.ArrayList;
import java.util.List;

import static app.Constants.INF;
import static engine.core.entity.Piece.CHECKMATE_VALUE;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Minimax {
    private static final int NULL_MOVE_MIN_DEPTH = 3;
    private static final int NULL_MOVE_REDUCTION = 2;

    private Bitboard bitboard;
    private final MoveGenerator moveGenerator;
    private final Evaluator evaluator;
    private final TranspositionTable transpositionTable;
    private final MoveOrderer moveOrderer;
    private int ply;
    private boolean interrupted = false;
    private SearchStats searchStats;
    private int bestMoveThisIteration;
    private int bestScoreThisIteration;
    private int bestMove;
    private int bestEval;

    public Minimax(MoveGenerator moveGenerator, Evaluator evaluator, TranspositionTable transpositionTable, MoveOrderer moveOrderer) {
        this.moveGenerator = moveGenerator;
        this.evaluator = evaluator;
        this.transpositionTable = transpositionTable;
        this.moveOrderer = moveOrderer;
    }

    /**
     * Runs iterative deepening search.
     * @param depth maximum depth
     * @param isWhiteTurn
     * @return Search stats (debugging info)
     */
    public SearchStats initiateSearch(int depth, boolean isWhiteTurn) {
        searchStats = new SearchStats();
        interrupted = false;
        for (int i = 1; i <= depth; i++) {
            if (interrupted) {
                return searchStats;
            }
            search(i, -INF, INF, isWhiteTurn);
            searchStats.recordDepthIncreased();
            bestMove = bestMoveThisIteration;
            bestEval = bestScoreThisIteration;
        }
        return searchStats;
    }

    /**
     * Negamax search with alpha beta pruning, transposition table and quiescence search.
     * @param depth search depth
     * @param alpha best move for current player so far
     * @param beta best move for opposing player so far
     * @param isWhiteTurn
     * @return
     */
    public int search(int depth, int alpha, int beta, boolean isWhiteTurn) {
        if (interrupted) {
            return 0;
        }

        // dp style lookup
        TranspositionTable.TTEntry entry = transpositionTable.get(bitboard.getHash());
        if (entry != null && entry.key() == bitboard.getHash() && entry.depth() >= depth) {
            if (entry.flag() == transpositionTable.EXACT) {
                searchStats.recordExact();
                if (ply == 0) {
                    bestMoveThisIteration = entry.bestMove();
                    bestScoreThisIteration = entry.score();
                }
                return entry.score();
            }
            else if (entry.flag() == transpositionTable.LOWER_BOUND) {
                searchStats.recordLowerBound();
                alpha = max(alpha, entry.score());
            }
            else if (entry.flag() == transpositionTable.UPPER_BOUND) {
                searchStats.recordUpperBound();
                beta = Math.min(beta, entry.score());
            }
            if (alpha >= beta && ply > 0) {
                searchStats.recordTTCutoff();
                if (ply == 0) {
                    bestMoveThisIteration = entry.bestMove();
                    bestScoreThisIteration = entry.score();
                }
                return entry.score();
            }
        }

        searchStats.recordNodeSearched();

        // recursion base case
        if (depth == 0) {
            return quiescenceSearch(alpha, beta, isWhiteTurn, 4);
        }

        // generate move list
        int[] moves = moveGenerator.generateLegalMoves(isWhiteTurn).clone();
        int moveCounter = moveGenerator.getMoveCounter();
        moveGenerator.clearMoves();

        // checkmate and stalemate handling
        boolean check = moveGenerator.isKingInCheck(isWhiteTurn);
        if (moveCounter == 0) {
            if (check) {
                searchStats.recordCheckmate();
                return -CHECKMATE_VALUE + ply;
            } else {
                searchStats.recordStalemate();
                return 0;
            }
        }

        // null move pruning: if we can skip a move entirely ("pass") and still fail high,
        // the position is so good a real move will too, so cut off without searching it.
        // Excluded at the root (ply 0 always needs a real bestMove), while in check (passing
        // while in check isn't legal, so the resulting search would be unsound), near mate
        // scores (a null-move-derived mate distance isn't trustworthy), and when the side to
        // move has no non-pawn material (king+pawn endgames are exactly where "zugzwang" - a
        // position where passing would in fact be an improvement - is common, which null move
        // pruning assumes never happens).
        if (ply > 0 && depth >= NULL_MOVE_MIN_DEPTH && !check
                && beta < CHECKMATE_VALUE - 1000 && hasNonPawnMaterial(isWhiteTurn)) {
            bitboard.backupState();
            bitboard.makeNullMove();
            ply++;

            int nullMoveScore = -search(depth - 1 - NULL_MOVE_REDUCTION, -beta, -beta + 1, !isWhiteTurn);

            ply--;
            bitboard.restoreState();

            if (interrupted) {
                return 0;
            }
            if (nullMoveScore >= beta) {
                searchStats.recordNullMoveCutoff();
                return beta;
            }
        }

        int bestMoveThisPosition = moves[0];
        // search best move from previous iteration first
        if (transpositionTable.get(bitboard.getHash()) != null) {
            bestMoveThisPosition = transpositionTable.get(bitboard.getHash()).bestMove();
            moveOrderer.sortPVFirst(moves, moveCounter, bestMoveThisPosition);
            bestMoveThisPosition = moves[0];
        }

        // in case best move is not found in this node, we only store upper bound
        int ttFlag = transpositionTable.UPPER_BOUND;

        // iterate through moves and search recursively
        for (int i = 0; i < moveCounter; i++) {
            bitboard.backupState();
            bitboard.makeMove(moves[i], isWhiteTurn, false);
            ply++;

            int score = -search(depth - 1, -beta, -alpha, !isWhiteTurn);

            ply--;
            bitboard.restoreState();

            // if the search was interrupted, just return before updating TT
            if (interrupted) {
                return 0;
            }

            // beta cutoff
            if (score >= beta) {
                searchStats.recordBetaCutoff();
                transpositionTable.put(bitboard.getHash(), moves[i], beta, depth, transpositionTable.LOWER_BOUND);
                return beta;
            }

            // new alpha
            if (score > alpha) {
                searchStats.recordBestMove();
                alpha = score;
                bestMoveThisPosition = moves[i];
                ttFlag = transpositionTable.EXACT;

                if (ply == 0) {
                    bestMoveThisIteration = bestMoveThisPosition;
                    bestScoreThisIteration = score;
                }
            }
        }

        transpositionTable.put(bitboard.getHash(), bestMoveThisPosition, alpha, depth, ttFlag);
        return alpha;
    }

    private boolean hasNonPawnMaterial(boolean isWhiteTurn) {
        long[] pieces = bitboard.getPieces();
        if (isWhiteTurn) {
            return pieces[Piece.WHITE_KNIGHT] != 0 || pieces[Piece.WHITE_BISHOP] != 0
                    || pieces[Piece.WHITE_ROOK] != 0 || pieces[Piece.WHITE_QUEEN] != 0;
        } else {
            return pieces[Piece.BLACK_KNIGHT] != 0 || pieces[Piece.BLACK_BISHOP] != 0
                    || pieces[Piece.BLACK_ROOK] != 0 || pieces[Piece.BLACK_QUEEN] != 0;
        }
    }

    private int quiescenceSearch(int alpha, int beta, boolean isWhiteTurn, int depth) {
        if (interrupted) {
            return 0;
        }

        // recursion base case
        if (depth == 0) {
            return (isWhiteTurn ? 1 : -1) * evaluator.evaluateAdvanced(bitboard);
        }

        int standPat = (isWhiteTurn ? 1 : -1) * evaluator.evaluateAdvanced(bitboard);
        if (standPat >= beta) {
            return beta;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        // generate move list
        int[] moves = moveGenerator.generateLegalMoves(isWhiteTurn).clone();
        int moveCounter = moveGenerator.getMoveCounter();
        moveGenerator.clearMoves();

        // filter out captures only
        List<Integer> captures = new ArrayList<>();
        for (int i = 0; i < moveCounter; i++) {
            if (!MoveEncoder.isMoveCapture(moves[i])) {
                continue;
            }
            captures.add(moves[i]);
        }
        // sort captures
        moveOrderer.sortCaptures(captures, bitboard);

        // try every capture
        for (int capture :  captures) {
            bitboard.backupState();
            bitboard.makeMove(capture, isWhiteTurn, true);
            ply++;
//            bitboard.logBoardState();
//            System.out.println(ply);

            int score = -quiescenceSearch(-beta, -alpha, !isWhiteTurn, depth - 1);

            ply--;
            bitboard.restoreState();

            // beta cutoff
            if (score >= beta) {
                return beta;
            }

            // new alpha
            if (score > alpha) {
                alpha = score;
            }
        }

        return alpha;
    }

    /**
     * Find all PV moves up to a max depth.
     * @param depth max depth
     * @param isWhiteTurn
     * @return list of PV moves
     */
    public List<Integer> getPrincipalVariation(int depth, boolean isWhiteTurn) {
        List<Integer> pv = new ArrayList<>();
        // limit the max depth to 10 to avoid infinite recursion in case of key collisions
        int maxDepth = min(depth, 10);
        while (maxDepth-- > 0) {
            TranspositionTable.TTEntry entry = transpositionTable.get(bitboard.getHash());
            if (entry == null || entry.bestMove() == -1) {
                break;
            }
            pv.add(entry.bestMove());
            bitboard.backupState();
            bitboard.makeMove(entry.bestMove(), isWhiteTurn, false);
            isWhiteTurn = !isWhiteTurn;
        }
        for (int i = pv.size() - 1; i >= 0; i--) {
            bitboard.restoreState();
        }
        return pv;
    }

    public void setBitboard(Bitboard bitboard) {
        this.bitboard = bitboard;
        ply = 0;
    }

    public void interruptSearch() {
        interrupted = true;
    }

    public int getBestMove() {
        return bestMove;
    }

    public int getBestEval() {
        return bestEval;
    }
}
