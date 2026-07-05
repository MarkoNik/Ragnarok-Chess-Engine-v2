package engine.search;

import org.junit.jupiter.api.Test;
import uci.command.GoCommandWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeManagerTest {

    @Test
    void moveTime_isUsedDirectly() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.moveTime = 750;
        assertEquals(750, TimeManager.computeBudgetMs(go, true));
        assertEquals(750, TimeManager.computeBudgetMs(go, false));
    }

    @Test
    void moveTime_takesPriorityOverEverythingElse() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.moveTime = 200;
        go.wtime = 60000;
        go.depth = 10;
        assertEquals(200, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void wtime_usedForWhite_btimeForBlack() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.wtime = 30_000;
        go.btime = 9_000;
        go.movesToGo = 30;

        // 30000/30 - 50 overhead = 950
        assertEquals(950, TimeManager.computeBudgetMs(go, true));
        // 9000/30 - 50 overhead = 250
        assertEquals(250, TimeManager.computeBudgetMs(go, false));
    }

    @Test
    void increment_isAddedToBudget() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.wtime = 30_000;
        go.winc = 500;
        go.movesToGo = 30;

        // 30000/30 + 500 - 50 = 1450
        assertEquals(1450, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void missingMovesToGo_fallsBackToDefaultEstimate() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.wtime = 30_000;
        // default assumed moves remaining is 30: 30000/30 - 50 = 950
        assertEquals(950, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void budgetNeverGoesBelowMinimum_evenWithVeryLittleTimeLeft() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.wtime = 10; // almost flagging
        go.movesToGo = 30;
        assertTrue(TimeManager.computeBudgetMs(go, true) >= 50);
    }

    @Test
    void depthOnly_isUnbounded() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.depth = 6;
        assertEquals(-1, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void nodesOnly_isUnbounded() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.nodes = 100_000;
        assertEquals(-1, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void mateOnly_isUnbounded() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.mate = 3;
        assertEquals(-1, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void nothingSpecified_fallsBackToBoundedDefault() {
        GoCommandWrapper go = new GoCommandWrapper();
        assertEquals(3000, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void infinite_fallsBackToBoundedDefault_sinceStopIsNotYetSupported() {
        GoCommandWrapper go = new GoCommandWrapper();
        go.infinite = true;
        assertEquals(3000, TimeManager.computeBudgetMs(go, true));
    }

    @Test
    void schedule_firesCallbackAfterBudgetElapses() throws InterruptedException {
        TimeManager timeManager = new TimeManager();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        timeManager.schedule(50, latch::countDown);

        assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS), "timeout callback should have fired");
    }

    @Test
    void schedule_cancelPreventsCallback() throws InterruptedException {
        TimeManager timeManager = new TimeManager();
        java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean(false);

        timeManager.schedule(50, () -> fired.set(true));
        timeManager.cancel();

        Thread.sleep(150);
        assertTrue(!fired.get(), "cancelled timeout should not fire");
    }

    @Test
    void schedule_replacesPreviousPendingTimeout() throws InterruptedException {
        TimeManager timeManager = new TimeManager();
        java.util.concurrent.atomic.AtomicBoolean firstFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.CountDownLatch secondFired = new java.util.concurrent.CountDownLatch(1);

        timeManager.schedule(50, () -> firstFired.set(true));
        timeManager.schedule(20, secondFired::countDown); // should cancel the first before it fires

        assertTrue(secondFired.await(2, java.util.concurrent.TimeUnit.SECONDS));
        Thread.sleep(100); // give the (cancelled) first timer a chance to fire if it wrongly survived
        assertTrue(!firstFired.get(), "scheduling a new timeout should cancel the previous one");
    }

    @Test
    void schedule_withUnboundedBudget_doesNotScheduleAnything() throws InterruptedException {
        TimeManager timeManager = new TimeManager();
        java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean(false);

        timeManager.schedule(-1, () -> fired.set(true));

        Thread.sleep(100);
        assertTrue(!fired.get(), "an unbounded budget should never schedule a timeout");
    }
}
