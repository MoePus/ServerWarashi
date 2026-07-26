package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfSamplerSessionTest {
    @Test
    void jitterCanUseMinimumHalfInterval() {
        assertEquals(500L, PerfSamplerSession.jitteredDelay(1_000L, 0L));
    }

    @Test
    void jitterStaysBelowOneAndAHalfIntervals() {
        assertEquals(1_499L, PerfSamplerSession.jitteredDelay(1_000L, -1L));
    }

    @Test
    void publishesSnapshotAfterTwentyClosedTicks() throws InterruptedException {
        PerfSamplerSession session = new PerfSamplerSession(1L, 1_000);
        session.start();
        try {
            for (int tick = 0; tick < 20; tick++) {
                session.beginTick(null);
                session.endTick(1_000_000L);
            }
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (session.latestReport().tickCount() < 20 && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }
            assertEquals(20L, session.latestReport().tickCount());
        } finally {
            session.stop();
        }
    }
}
