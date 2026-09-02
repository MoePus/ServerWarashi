package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimaryObservationRingTest {
    private static final long INTERVAL_NANOS = 1_000L;

    @Test
    void productionCapacityCoversTwoSecondsAtMinimumJitter() {
        PrimaryObservationRing ring = new PrimaryObservationRing(1_000_000L);

        assertEquals(4_096, ring.capacity());
        assertTrue(ring.capacity() * 500_000L >= 2_000_000_000L);
    }

    @Test
    void matchesOnlyStableAdjacentObservationsFromTheExpectedTick() {
        PrimaryObservationRing ring = ring();
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(200L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);

        assertEquals(PrimaryObservationRing.CoarseState.UNKNOWN, ring.match(150L, 7L));
    }

    @Test
    void rejectsStateChanges() {
        PrimaryObservationRing ring = ring();
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(200L, 7L, PrimaryObservationRing.CoarseState.TRACKED);

        assertNull(ring.match(150L, 7L));
    }

    @Test
    void rejectsTickBoundaryChanges() {
        PrimaryObservationRing ring = ring();
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(200L, 8L, PrimaryObservationRing.CoarseState.UNKNOWN);

        assertNull(ring.match(150L, 7L));
        assertNull(ring.match(150L, 8L));
    }

    @Test
    void rejectsMissingSuccessorObservation() {
        PrimaryObservationRing ring = ring();
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);

        assertNull(ring.match(150L, 7L));
    }

    @Test
    void rejectsObservationGapsLargerThanFourPrimaryIntervals() {
        PrimaryObservationRing ring = ring();
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.HANDLER);
        ring.record(4_101L, 7L, PrimaryObservationRing.CoarseState.HANDLER);

        assertNull(ring.match(200L, 7L));
    }

    @Test
    void overwrittenObservationsCannotBeRead() {
        PrimaryObservationRing ring = new PrimaryObservationRing(4, INTERVAL_NANOS);
        ring.record(100L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(200L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(300L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(400L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        assertEquals(PrimaryObservationRing.CoarseState.UNKNOWN, ring.match(250L, 7L));

        ring.record(500L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);
        ring.record(600L, 7L, PrimaryObservationRing.CoarseState.UNKNOWN);

        assertNull(ring.match(250L, 7L));
    }

    private static PrimaryObservationRing ring() {
        return new PrimaryObservationRing(8, INTERVAL_NANOS);
    }
}
