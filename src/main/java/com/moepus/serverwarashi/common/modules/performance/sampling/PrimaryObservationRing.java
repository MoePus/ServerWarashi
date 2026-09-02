package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

final class PrimaryObservationRing {
    // 4096 * (1 ms minimum interval * 0.5 minimum jitter) = 2.048 seconds.
    static final int CAPACITY = 4_096;
    private static final int MAX_GAP_INTERVALS = 4;
    private static final VarHandle SEQUENCE_HANDLE =
            MethodHandles.arrayElementVarHandle(long[].class);
    private static final CoarseState[] STATES = CoarseState.values();

    private final long[] sequences;
    private final long[] timestamps;
    private final long[] tickIds;
    private final byte[] states;
    private final int indexMask;
    private final long maximumGapNanos;
    private volatile long publishedSequence;
    private long nextSequence = 1L;

    PrimaryObservationRing(long primaryIntervalNanos) {
        this(CAPACITY, primaryIntervalNanos);
    }

    PrimaryObservationRing(int capacity, long primaryIntervalNanos) {
        if (capacity < 2 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Observation capacity must be a power of two");
        }
        if (primaryIntervalNanos <= 0L) {
            throw new IllegalArgumentException("Primary interval must be positive");
        }
        sequences = new long[capacity];
        timestamps = new long[capacity];
        tickIds = new long[capacity];
        states = new byte[capacity];
        indexMask = capacity - 1;
        maximumGapNanos = primaryIntervalNanos * MAX_GAP_INTERVALS;
    }

    void record(long timestampNanos, long tickId, CoarseState state) {
        long sequence = nextSequence++;
        int index = index(sequence);
        SEQUENCE_HANDLE.setRelease(sequences, index, 0L);
        timestamps[index] = timestampNanos;
        tickIds[index] = tickId;
        states[index] = (byte) state.ordinal();
        SEQUENCE_HANDLE.setRelease(sequences, index, sequence);
        publishedSequence = sequence;
    }

    CoarseState match(long timestampNanos, long expectedTickId) {
        long latest = publishedSequence;
        long earliest = Math.max(1L, latest - sequences.length + 1L);
        long afterSequence = 0L;
        long afterTimestamp = 0L;
        long afterTickId = 0L;
        byte afterState = 0;
        for (long sequence = latest; sequence >= earliest; sequence--) {
            int index = index(sequence);
            if (sequenceAt(index) != sequence) {
                return null;
            }
            long observedTimestamp = timestamps[index];
            long observedTickId = tickIds[index];
            byte observedState = states[index];
            if (sequenceAt(index) != sequence) {
                return null;
            }
            if (observedTimestamp >= timestampNanos) {
                afterSequence = sequence;
                afterTimestamp = observedTimestamp;
                afterTickId = observedTickId;
                afterState = observedState;
                continue;
            }
            return reliableMatch(sequence, observedTimestamp, observedTickId, observedState,
                    afterSequence, afterTimestamp, afterTickId, afterState, expectedTickId);
        }
        return null;
    }

    int capacity() {
        return sequences.length;
    }

    private CoarseState reliableMatch(long beforeSequence,
                                      long beforeTimestamp,
                                      long beforeTickId,
                                      byte beforeState,
                                      long afterSequence,
                                      long afterTimestamp,
                                      long afterTickId,
                                      byte afterState,
                                      long expectedTickId) {
        if (afterSequence != beforeSequence + 1L
                || beforeTickId != expectedTickId || afterTickId != expectedTickId
                || beforeState != afterState
                || afterTimestamp - beforeTimestamp > maximumGapNanos) {
            return null;
        }
        return STATES[beforeState];
    }

    private int index(long sequence) {
        return (int) sequence & indexMask;
    }

    private long sequenceAt(int index) {
        return (long) SEQUENCE_HANDLE.getAcquire(sequences, index);
    }

    enum CoarseState {
        UNKNOWN,
        HANDLER,
        TRACKED
    }
}
