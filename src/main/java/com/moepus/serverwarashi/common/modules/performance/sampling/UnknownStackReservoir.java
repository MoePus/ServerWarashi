package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class UnknownStackReservoir {
    static final int CAPACITY = 256;
    // PerfSamplerSession.stateLock serializes record, tick completion, and snapshots.
    private final AtomicReferenceArray<StackTraceElement[]> stacks =
            new AtomicReferenceArray<>(CAPACITY);
    private final long[] tickIds = new long[CAPACITY];
    private final long[] tickTotals = new long[CAPACITY];
    private final SplittableRandom random;
    private long revision;
    private long totalObserved;

    UnknownStackReservoir(long seed) {
        random = new SplittableRandom(seed);
    }

    void record(StackTraceElement[] stack, long tickId) {
        if (stack == null || stack.length == 0 || tickId <= 0L) {
            return;
        }
        long observed = ++totalObserved;
        int slot = replacementSlot(observed);
        if (slot >= 0) {
            tickTotals[slot] = 0L;
            tickIds[slot] = tickId;
            stacks.lazySet(slot, stack);
        }
        revision++;
    }

    UnknownStackSnapshot snapshot(long reportId, long tickCount, int intervalMicros) {
        return snapshot(new SnapshotParameters(
                reportId, tickCount, intervalMicros, Long.MAX_VALUE,
                0L, 0L, 0L, false));
    }

    void completeTick(long tickId, long totalSamples) {
        if (tickId <= 0L || totalSamples <= 0L) {
            return;
        }
        int populated = (int) Math.min(totalObserved, CAPACITY);
        boolean changed = false;
        for (int index = 0; index < populated; index++) {
            if (tickIds[index] == tickId && stacks.get(index) != null) {
                tickTotals[index] = totalSamples;
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    UnknownStackSnapshot snapshot(SnapshotParameters parameters) {
        long observed = totalObserved;
        int populated = (int) Math.min(observed, CAPACITY);
        int retained = retainedCount(populated, parameters.maximumTickId);
        StackTraceElement[][] copiedStacks = new StackTraceElement[retained][];
        long[] copiedTickIds = new long[retained];
        long[] copiedTickTotals = new long[retained];
        var copy = new RetainedCopy(copiedStacks, copiedTickIds, copiedTickTotals);
        copyRetained(populated, copy, parameters);
        long includedObserved = Math.max(0L, observed - parameters.excludedObserved);
        var metadata = new UnknownStackSnapshot.Metadata(
                parameters.reportId, revision, includedObserved,
                parameters.tickCount, parameters.intervalMicros, parameters.finalized);
        var samples = new UnknownStackSnapshot.Samples(
                copiedStacks, copiedTickIds, copiedTickTotals);
        return new UnknownStackSnapshot(metadata, samples);
    }

    private int retainedCount(int populated, long maximumTickId) {
        int retained = 0;
        for (int index = 0; index < populated; index++) {
            if (tickIds[index] <= maximumTickId && stacks.get(index) != null) {
                retained++;
            }
        }
        return retained;
    }

    private void copyRetained(int populated,
                              RetainedCopy copy,
                              SnapshotParameters parameters) {
        int target = 0;
        for (int index = 0; index < populated; index++) {
            long tickId = tickIds[index];
            StackTraceElement[] stack = stacks.get(index);
            if (tickId > parameters.maximumTickId || stack == null) {
                continue;
            }
            copy.tickIds[target] = tickId;
            copy.stacks[target] = stack;
            long tickTotal = tickTotals[index];
            copy.tickTotals[target] = tickId == parameters.activeTickId
                    ? parameters.activeTickTotal : tickTotal;
            target++;
        }
    }

    private int replacementSlot(long observed) {
        if (observed <= CAPACITY) {
            return (int) observed - 1;
        }
        long selected = random.nextLong(observed);
        return selected < CAPACITY ? (int) selected : -1;
    }

    record SnapshotParameters(long reportId,
                              long tickCount,
                              int intervalMicros,
                              long maximumTickId,
                              long excludedObserved,
                              long activeTickId,
                              long activeTickTotal,
                              boolean finalized) {
    }

    private record RetainedCopy(StackTraceElement[][] stacks,
                                long[] tickIds,
                                long[] tickTotals) {
    }
}
