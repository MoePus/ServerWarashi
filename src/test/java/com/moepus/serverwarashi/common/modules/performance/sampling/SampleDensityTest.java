package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleDensityTest {
    @Test
    void usesEmpiricalPeriodBeforeEnoughObservationsExist() {
        assertEquals(1_250_000.0D,
                SampleDensity.nanosPerAttempt(0L, 0L, 1_000), 0.000_001D);
    }

    @Test
    void smoothlyMovesFromPriorToObservedPeriod() {
        double prior = SampleDensity.nanosPerAttempt(0L, 0L, 1_000);
        double halfway = SampleDensity.nanosPerAttempt(512_000_000L, 256L, 1_000);
        double observed = SampleDensity.nanosPerAttempt(2_048_000_000L, 1_024L, 1_000);

        assertEquals(1_250_000.0D, prior, 0.000_001D);
        assertEquals(1_625_000.0D, halfway, 0.000_001D);
        assertEquals(1_850_000.0D, observed, 0.000_001D);
        assertTrue(halfway > prior && halfway < observed);
    }

    @Test
    void scalesPriorWithConfiguredInterval() {
        assertEquals(2_250_000.0D,
                SampleDensity.nanosPerAttempt(0L, 0L, 2_000), 0.000_001D);
    }
}
