package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickGcTimeTrackerTest {
    @Test
    void reportsCollectionTimeAddedSinceSnapshot() {
        AtomicLong total = new AtomicLong(40L);
        TickGcTimeTracker tracker = new TickGcTimeTracker(total::get);
        long snapshot = tracker.snapshot();

        total.set(47L);

        assertEquals(7L, tracker.elapsedSince(snapshot));
    }

    @Test
    void clampsAResetCollectionCounterToZero() {
        AtomicLong total = new AtomicLong(40L);
        TickGcTimeTracker tracker = new TickGcTimeTracker(total::get);
        long snapshot = tracker.snapshot();

        total.set(3L);

        assertEquals(0L, tracker.elapsedSince(snapshot));
    }
}
