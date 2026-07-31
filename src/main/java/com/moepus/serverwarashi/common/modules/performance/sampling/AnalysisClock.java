package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.function.LongSupplier;

final class AnalysisClock {
    enum State {
        NEW,
        RUNNING,
        PAUSED,
        STOPPED
    }

    private final LongSupplier nanoTime;
    private volatile State state = State.NEW;
    private long accumulatedNanos;
    private long runningSinceNanos;

    AnalysisClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    State state() {
        return state;
    }

    void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Analysis clock has already started");
        }
        runningSinceNanos = nanoTime.getAsLong();
        state = State.RUNNING;
    }

    boolean pause() {
        if (state != State.RUNNING) {
            return false;
        }
        accumulatedNanos += nanoTime.getAsLong() - runningSinceNanos;
        state = State.PAUSED;
        return true;
    }

    boolean resume() {
        if (state != State.PAUSED) {
            return false;
        }
        runningSinceNanos = nanoTime.getAsLong();
        state = State.RUNNING;
        return true;
    }

    void stop() {
        if (state == State.RUNNING) {
            accumulatedNanos += nanoTime.getAsLong() - runningSinceNanos;
        }
        state = State.STOPPED;
    }

    long elapsedNanos() {
        if (state == State.RUNNING) {
            return accumulatedNanos + nanoTime.getAsLong() - runningSinceNanos;
        }
        return accumulatedNanos;
    }
}
