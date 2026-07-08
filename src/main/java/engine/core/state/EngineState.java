package engine.core.state;

import app.Constants;
import app.UciLogger;
import engine.config.EngineConfig;
import engine.core.bitboard.BitboardHelper;
import engine.search.Evaluator;
import engine.search.Minimax;
import engine.search.MoveGenerator;
import engine.search.MoveOrderer;
import engine.search.TimeManager;
import engine.util.PerftDriver;
import engine.util.bits.FenParser;
import uci.command.GoCommandWrapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static app.Constants.INF;

public class EngineState {
    private GameState gameState;

    private final EngineConfig config;
    private int bestMove;
    private final MoveGenerator moveGenerator;
    private final Minimax minimax;
    private final TranspositionTable transpositionTable;
    private final TimeManager timeManager = new TimeManager();

    public EngineState() {
        config = loadConfig();
        BitboardHelper bitboardHelper = new BitboardHelper();
        moveGenerator = new MoveGenerator(bitboardHelper);
        Evaluator evaluator = new Evaluator();
        transpositionTable = new TranspositionTable();
        MoveOrderer moveOrderer = new MoveOrderer();
        minimax = new Minimax(moveGenerator, evaluator, transpositionTable, moveOrderer, config);
        gameState = FenParser.parseFEN(Constants.INITIAL_FEN);
    }

    /** Startup defaults come from -Dengine.config=&lt;path&gt; (a java.util.Properties
     *  file), falling back to all features enabled if unset. UCI "setoption" can
     *  still override individual flags afterwards - see setConfigOption(). */
    private static EngineConfig loadConfig() {
        String configPath = System.getProperty("engine.config");
        if (configPath == null) {
            return new EngineConfig();
        }
        try {
            return EngineConfig.loadFromFile(Path.of(configPath));
        } catch (IOException e) {
            UciLogger.error("Failed to load engine.config from " + configPath + ": " + e.getMessage());
            return new EngineConfig();
        }
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

    public EngineConfig getConfig() {
        return config;
    }

    public void setConfigOption(String name, String value) {
        try {
            config.set(name, value);
        } catch (IllegalArgumentException e) {
            UciLogger.warn("Unknown engine option: " + name);
        }
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
