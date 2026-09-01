package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class UnknownStackComputationCache {
    static final int MAX_IN_FLIGHT = 4;
    private final Map<Key, Computation> computations = new HashMap<>();
    private final Executor executor;

    UnknownStackComputationCache(Executor executor) {
        this.executor = executor;
    }

    synchronized Computation acquire(UnknownStackSnapshot snapshot) {
        Key key = new Key(snapshot.reportId(), snapshot.revision(), snapshot.finalized());
        Computation computation = computations.get(key);
        if (computation == null) {
            Computation reportComputation = computationForReport(
                    snapshot.reportId(), snapshot.finalized());
            if (reportComputation != null) {
                return reportComputation;
            }
        }
        if (computation == null && computations.size() >= MAX_IN_FLIGHT) {
            return null;
        }
        if (computation == null) {
            CompletableFuture<UnknownStackPreparedDetails> future = CompletableFuture.supplyAsync(
                    () -> UnknownStackPreparedDetails.prepare(snapshot), executor);
            computation = new Computation(key, future);
            computations.put(key, computation);
        }
        return computation;
    }

    synchronized Computation current(long reportId, boolean finalized) {
        return computationForReport(reportId, finalized);
    }

    private Computation computationForReport(long reportId, boolean finalized) {
        for (Map.Entry<Key, Computation> entry : computations.entrySet()) {
            if (entry.getKey().reportId == reportId
                    && entry.getKey().finalized == finalized) {
                return entry.getValue();
            }
        }
        return null;
    }

    synchronized void release(Computation computation) {
        computations.remove(computation.key, computation);
    }

    synchronized void clear() {
        computations.clear();
    }

    record Computation(Key key, CompletableFuture<UnknownStackPreparedDetails> future) {
    }

    record Key(long reportId, long revision, boolean finalized) {
    }
}
