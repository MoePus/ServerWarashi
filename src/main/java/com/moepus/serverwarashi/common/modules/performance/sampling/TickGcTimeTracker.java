package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.LongSupplier;

final class TickGcTimeTracker {
    private final LongSupplier totalCollectionMillis;

    TickGcTimeTracker() {
        List<GarbageCollectorMXBean> collectors = List.copyOf(ManagementFactory.getGarbageCollectorMXBeans());
        totalCollectionMillis = () -> totalCollectionMillis(collectors);
    }

    TickGcTimeTracker(LongSupplier totalCollectionMillis) {
        this.totalCollectionMillis = totalCollectionMillis;
    }

    long snapshot() {
        return totalCollectionMillis.getAsLong();
    }

    long elapsedSince(long snapshot) {
        return Math.max(0L, snapshot() - snapshot);
    }

    private static long totalCollectionMillis(List<GarbageCollectorMXBean> collectors) {
        long total = 0L;
        for (GarbageCollectorMXBean collector : collectors) {
            long collectionTime = collector.getCollectionTime();
            if (collectionTime > 0L) {
                total += collectionTime;
            }
        }
        return total;
    }
}
