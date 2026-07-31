package com.moepus.serverwarashi.common.modules.performance.sampling;

final class SampleDensity {
    private static final long EMPIRICAL_WAKEUP_OVERHEAD_NANOS = 250_000L;
    private static final double PRIOR_ATTEMPTS = 256.0D;

    private SampleDensity() {
    }

    static double nanosPerAttempt(long activeElapsedNanos,
                                  long attempts,
                                  int configuredIntervalMicros) {
        double priorNanos = configuredIntervalMicros * 1_000.0D
                + EMPIRICAL_WAKEUP_OVERHEAD_NANOS;
        if (attempts <= 0L || activeElapsedNanos <= 0L) {
            return priorNanos;
        }
        double observedNanos = activeElapsedNanos / (double) attempts;
        double observedWeight = attempts / (attempts + PRIOR_ATTEMPTS);
        return priorNanos + observedWeight * (observedNanos - priorNanos);
    }
}
