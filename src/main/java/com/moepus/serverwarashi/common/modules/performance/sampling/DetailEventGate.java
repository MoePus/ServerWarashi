package com.moepus.serverwarashi.common.modules.performance.sampling;

final class DetailEventGate {
    private long minimumSpacingNanos;
    private long lastAcceptedNanos = Long.MIN_VALUE;

    void reset(long spacingNanos) {
        minimumSpacingNanos = spacingNanos;
        lastAcceptedNanos = Long.MIN_VALUE;
    }

    boolean accept(long timestampNanos) {
        if (lastAcceptedNanos != Long.MIN_VALUE
                && timestampNanos - lastAcceptedNanos < minimumSpacingNanos) {
            return false;
        }
        lastAcceptedNanos = timestampNanos;
        return true;
    }
}
