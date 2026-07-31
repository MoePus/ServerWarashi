package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisClockTest {
    @Test
    void excludesPausedWallTimeAndContinuesAfterResume() {
        AtomicLong now = new AtomicLong(1_000L);
        AnalysisClock clock = new AnalysisClock(now::get);

        clock.start();
        now.addAndGet(3_000L);
        assertTrue(clock.pause());
        now.addAndGet(20_000L);
        assertEquals(3_000L, clock.elapsedNanos());

        assertTrue(clock.resume());
        now.addAndGet(2_000L);
        clock.stop();
        now.addAndGet(20_000L);

        assertEquals(5_000L, clock.elapsedNanos());
        assertEquals(AnalysisClock.State.STOPPED, clock.state());
    }

    @Test
    void repeatedPauseAndResumeSignalsAreIdempotent() {
        AnalysisClock clock = new AnalysisClock(() -> 10L);

        assertFalse(clock.pause());
        clock.start();
        assertFalse(clock.resume());
        assertTrue(clock.pause());
        assertFalse(clock.pause());
        assertTrue(clock.resume());
        assertFalse(clock.resume());
    }
}
