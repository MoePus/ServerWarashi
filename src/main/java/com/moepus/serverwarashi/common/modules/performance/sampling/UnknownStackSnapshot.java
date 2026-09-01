package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.Objects;

public final class UnknownStackSnapshot {
    private final Metadata metadata;
    private final Samples samples;

    UnknownStackSnapshot(Metadata metadata, Samples samples) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(samples, "samples");
        int retained = samples.stacks.length;
        if (retained != samples.tickIds.length || retained != samples.tickTotals.length
                || retained > UnknownStackReservoir.CAPACITY
                || metadata.totalObserved < retained) {
            throw new IllegalArgumentException("Invalid Unknown stack snapshot size");
        }
        for (int index = 0; index < retained; index++) {
            Objects.requireNonNull(samples.stacks[index], "stack");
            if (samples.tickIds[index] <= 0L) {
                throw new IllegalArgumentException("Unknown stack tick IDs must be positive");
            }
            if (samples.tickTotals[index] < 0L) {
                throw new IllegalArgumentException("Unknown stack tick totals cannot be negative");
            }
        }
        this.metadata = metadata;
        this.samples = samples;
    }

    public long reportId() {
        return metadata.reportId;
    }

    public long revision() {
        return metadata.revision;
    }

    public long totalObserved() {
        return metadata.totalObserved;
    }

    public long tickCount() {
        return metadata.tickCount;
    }

    public int intervalMicros() {
        return metadata.intervalMicros;
    }

    public boolean finalized() {
        return metadata.finalized;
    }

    public int retainedCount() {
        return samples.stacks.length;
    }

    StackTraceElement[] stackAt(int index) {
        return samples.stacks[index];
    }

    long tickIdAt(int index) {
        return samples.tickIds[index];
    }

    long tickTotalAt(int index) {
        return samples.tickTotals[index];
    }

    record Metadata(long reportId,
                    long revision,
                    long totalObserved,
                    long tickCount,
                    int intervalMicros,
                    boolean finalized) {
        Metadata {
            if (reportId <= 0L || revision < 0L || totalObserved < 0L
                    || tickCount < 0L || intervalMicros <= 0) {
                throw new IllegalArgumentException("Invalid Unknown stack snapshot metadata");
            }
        }
    }

    record Samples(StackTraceElement[][] stacks, long[] tickIds, long[] tickTotals) {
        Samples {
            Objects.requireNonNull(stacks, "stacks");
            Objects.requireNonNull(tickIds, "tickIds");
            Objects.requireNonNull(tickTotals, "tickTotals");
        }
    }
}
