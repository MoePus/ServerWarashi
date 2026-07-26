package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.mixin.accessor.TickTaskAccessor;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfWorkTrackerTest {
    @AfterEach
    void tearDown() {
        PerfWorkTracker.deactivate();
    }

    @Test
    void nestedTaskExitRestoresOuterSlotAndUnwrapsTickTask() {
        TestEventLoop eventLoop = new TestEventLoop();
        OuterTask outer = new OuterTask();
        InnerTask inner = new InnerTask();
        PerfWorkTracker.activate(Thread.currentThread());

        assertTrue(PerfWorkTracker.enterTask(eventLoop, outer));
        assertEquals(OuterTask.class, PerfWorkTracker.snapshot().workClass());
        assertTrue(PerfWorkTracker.enterTask(eventLoop, new AccessibleTickTask(inner)));
        assertEquals(InnerTask.class, PerfWorkTracker.snapshot().workClass());

        assertTrue(PerfWorkTracker.exit(TickType.TASK));
        assertEquals(OuterTask.class, PerfWorkTracker.snapshot().workClass());
        assertTrue(PerfWorkTracker.exit(TickType.TASK));
        assertNull(PerfWorkTracker.snapshot());
    }

    private static final class TestEventLoop extends BlockableEventLoop<Runnable> {
        private TestEventLoop() {
            super("test-loop");
        }

        @Override
        protected Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return Thread.currentThread();
        }
    }

    private static final class AccessibleTickTask extends TickTask implements TickTaskAccessor {
        private final Runnable runnable;

        private AccessibleTickTask(Runnable runnable) {
            super(0, runnable);
            this.runnable = runnable;
        }

        @Override
        public Runnable serverWarashi$getRunnable() {
            return runnable;
        }
    }

    private static final class OuterTask implements Runnable {
        @Override
        public void run() {
        }
    }

    private static final class InnerTask implements Runnable {
        @Override
        public void run() {
        }
    }
}
