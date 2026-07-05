package engine.search;

import uci.command.GoCommandWrapper;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Computes how long (ms) a search should run for given a UCI "go" command, and
 * owns the single cancelable timer used to enforce it.
 *
 * Replaces the previous approach of spawning a brand new, un-cancelable Thread
 * on every "go" that always slept a hardcoded 3000ms regardless of
 * movetime/wtime/btime. That ignored the GUI's requested time control, and
 * left stale timers alive that could fire during a later, unrelated search
 * and truncate it early.
 */
public class TimeManager {
    private static final int DEFAULT_MOVES_REMAINING = 30;
    private static final int MOVE_OVERHEAD_MS = 50;
    private static final int MIN_BUDGET_MS = 50;

    /**
     * Used only when the "go" command gives us nothing to base a budget on: no
     * movetime, no wtime/btime, and no depth/nodes/mate limit either (a bare
     * "go", or "go infinite"). The engine can't yet be interrupted by a UCI
     * "stop" command (see StopCommand), so an unbounded search here would hang
     * forever; this keeps that case bounded until "stop" is implemented.
     */
    private static final int UNBOUNDED_FALLBACK_MS = 3000;

    private final Timer timer = new Timer("search-timer", true);
    private TimerTask pendingTask;

    /**
     * Returns the search budget in ms, or -1 if the search should run
     * unbounded by time (depth/nodes/mate-limited).
     */
    public static int computeBudgetMs(GoCommandWrapper go, boolean isWhiteTurn) {
        if (go.moveTime != -1) {
            return go.moveTime;
        }

        int myTime = isWhiteTurn ? go.wtime : go.btime;
        if (myTime != -1) {
            int myInc = isWhiteTurn ? go.winc : go.binc;
            int inc = myInc != -1 ? myInc : 0;
            int movesRemaining = go.movesToGo != -1 ? go.movesToGo : DEFAULT_MOVES_REMAINING;
            int budget = myTime / movesRemaining + inc - MOVE_OVERHEAD_MS;
            return Math.max(budget, MIN_BUDGET_MS);
        }

        if (go.depth != -1 || go.nodes != -1 || go.mate != -1) {
            return -1;
        }

        return UNBOUNDED_FALLBACK_MS;
    }

    /**
     * Cancels any previously scheduled timeout and schedules a new one, unless
     * budgetMs is negative (unbounded search).
     */
    public void schedule(int budgetMs, Runnable onTimeout) {
        cancel();
        if (budgetMs < 0) {
            return;
        }
        pendingTask = new TimerTask() {
            @Override
            public void run() {
                onTimeout.run();
            }
        };
        timer.schedule(pendingTask, budgetMs);
    }

    /** Cancels any pending timeout, e.g. once a search has finished naturally. */
    public void cancel() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
    }
}
