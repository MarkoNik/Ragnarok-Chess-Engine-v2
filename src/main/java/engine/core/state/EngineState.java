package engine.core.state;

import app.Constants;
import app.UciLogger;
import engine.core.bitboard.BitboardHelper;
import engine.search.Evaluator;
import engine.search.Minimax;
import engine.search.MoveGenerator;
import engine.search.MoveOrderer;
import engine.search.TimeManager;
import engine.util.PerftDriver;
import engine.util.bits.FenParser;
import uci.command.GoCommandWrapper;

import java.util.List;
import java.util.Map;

import static app.Constants.INF;

public class EngineState {
    private GameState gameState;

    // TODO make a set of available configuration parameters and their values
    private Map<String, String> configMap;
    private int bestMove;
    private final MoveGenerator moveGenerator;
    private final Minimax minimax;
    private final TranspositionTable transpositionTable;
    private final TimeManager timeManager = new TimeManager();

    public EngineState() {
        BitboardHelper bitboardHelper = new BitboardHelper();
        moveGenerator = new MoveGenerator(bitboardHelper);
        Evaluator evaluator = new Evaluator();
        transpositionTable = new TranspositionTable();
        MoveOrderer moveOrderer = new MoveOrderer();
        minimax = new Minimax(moveGenerator, evaluator, transpositionTable, moveOrderer);
        gameState = FenParser.parseFEN(Constants.INITIAL_FEN);
    }

    public void search(GoCommandWrapper goCommandWrapper) {
        // run perft test
        if (goCommandWrapper.perftDepth != -1) {
            PerftDriver perftDriver = new PerftDriver(this, gameState, moveGenerator);
            perftDriver.runPerftTest(goCommandWrapper.perftDepth);
            return;
        }

        moveGenerator.setBitboard(gameState.getBitboard());
        minimax.setBitboard(gameState.getBitboard());

        int depth = goCommandWrapper.depth;
        if (depth == -1) {
            depth = INF;
        }

        int budgetMs = TimeManager.computeBudgetMs(goCommandWrapper, gameState.isWhiteTurn());
        timeManager.schedule(budgetMs, minimax::interruptSearch);
        var searchStats = minimax.initiateSearch(depth, gameState.isWhiteTurn());
        timeManager.cancel();
        searchStats.logSearchStats();

        int eval = minimax.getBestEval();
        System.out.print("info score cp " + eval + " ");

        List<Integer> pv = minimax.getPrincipalVariation(depth, gameState.isWhiteTurn());
        System.out.print("pv ");
        for (int move : pv) {
            System.out.print(FenParser.moveToAlgebraic(move) + " ");
        }
        System.out.println();

        bestMove = minimax.getBestMove();
        gameState.getBitboard().backupState();
        gameState.playMove(bestMove);
        moveGenerator.clearMoves();
    }

    public int[] generateLegalMoves() {
        moveGenerator.setBitboard(gameState.getBitboard());
        return moveGenerator.generateLegalMoves(gameState.isWhiteTurn());
    }

    public int getMoveCounter() {
        return moveGenerator.getMoveCounter();
    }

    public void clearMoves() {
        moveGenerator.clearMoves();
    }

    public String getConfigOption(String name) {
        if (!configMap.containsKey(name)) {
            UciLogger.warn("Option with the name: " + name + " is requested but has not been set.");
        }
        return configMap.get(name);
    }
    public void setConfigOption(String name, String value) {
        configMap.put(name, value);
    }

    public int getBestMove() {
        return bestMove;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void logTTEntry() {
        transpositionTable.logTTEntry(gameState.getBitboard().getHash());
    }

    public void clearTT() {
        transpositionTable.clear();
    }
}
