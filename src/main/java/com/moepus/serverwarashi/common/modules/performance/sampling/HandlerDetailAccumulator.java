package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HandlerDetailAccumulator {
    private static final int MIN_EVIDENCE_SAMPLES = 2;
    private static final int MAX_DETAIL_KEYS = 4_096;
    private static final int INITIAL_TICK_CAPACITY = 256;
    private static final String GENERIC_HANDLER = "Handler";

    private final Map<PerfWorkTracker.Snapshot, Integer> evidence = new LinkedHashMap<>();
    private long[] ticks = new long[INITIAL_TICK_CAPACITY];
    private int[] samples = new int[INITIAL_TICK_CAPACITY];
    private TickSamples.TickableKey genericKey;
    private int tickSize;
    private int genericEvidence;

    void accept(TickSamples tick, long tickIndex) {
        removeGenericHandler(tick, tickIndex);
        tick.details.handlerEvidence().forEach(this::recordEvidence);
    }

    List<PerfReportData.TickableEntry> build(SampleMetricContext context) {
        if (tickSize == 0 || genericKey == null) {
            return List.of();
        }
        int totalEvidence = genericEvidence + evidenceCount();
        if (totalEvidence < MIN_EVIDENCE_SAMPLES) {
            return List.of(genericEntry(context));
        }
        List<Bucket> buckets = detailBuckets();
        Map<TickSamples.TickableKey, PerfSampleAggregator.TickableAggregate> aggregates =
                new LinkedHashMap<>();
        for (int index = 0; index < tickSize; index++) {
            allocate(samples[index], totalEvidence, buckets);
            recordAllocated(aggregates, buckets, ticks[index]);
        }
        return aggregates.entrySet().stream()
                .map(entry -> entry.getValue().toReport(entry.getKey(), context))
                .toList();
    }

    private void removeGenericHandler(TickSamples tick, long tickIndex) {
        var iterator = tick.tickables.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            TickSamples.TickableKey key = entry.getKey();
            if (!isGenericHandler(key)) {
                continue;
            }
            iterator.remove();
            genericKey = genericKey == null ? key : genericKey;
            appendTick(tickIndex, entry.getValue().count);
            return;
        }
    }

    private static boolean isGenericHandler(TickSamples.TickableKey key) {
        return key.type() == TickType.HANDLER
                && GENERIC_HANDLER.equals(key.displayName())
                && key.attributionSource() == PerfReportData.AttributionSource.TASK_CONTEXT;
    }

    private void appendTick(long tick, int count) {
        if (count <= 0) {
            return;
        }
        ensureTickCapacity();
        ticks[tickSize] = tick;
        samples[tickSize] = count;
        tickSize++;
    }

    private void ensureTickCapacity() {
        if (tickSize < ticks.length) {
            return;
        }
        int capacity = ticks.length << 1;
        ticks = Arrays.copyOf(ticks, capacity);
        samples = Arrays.copyOf(samples, capacity);
    }

    private void recordEvidence(PerfWorkTracker.Snapshot snapshot, int count) {
        Integer previous = evidence.get(snapshot);
        if (previous != null) {
            evidence.put(snapshot, previous + count);
        } else if (evidence.size() < MAX_DETAIL_KEYS) {
            evidence.put(snapshot, count);
        } else {
            genericEvidence += count;
        }
    }

    private int evidenceCount() {
        return evidence.values().stream().mapToInt(Integer::intValue).sum();
    }

    private PerfReportData.TickableEntry genericEntry(SampleMetricContext context) {
        PerfSampleAggregator.TickableAggregate aggregate =
                new PerfSampleAggregator.TickableAggregate();
        for (int index = 0; index < tickSize; index++) {
            aggregate.recordSamples(samples[index], ticks[index]);
        }
        return aggregate.toReport(genericKey, context);
    }

    private List<Bucket> detailBuckets() {
        List<Bucket> result = new ArrayList<>(evidence.size() + 1);
        evidence.forEach((snapshot, count) -> result.add(new Bucket(
                TickSamples.TickableKey.from(snapshot, SampleMetricMode.STATISTICAL,
                        PerfReportData.AttributionSource.ASYNC_STACK,
                        PerfReportData.Confidence.MEDIUM), count)));
        if (genericEvidence > 0) {
            result.add(new Bucket(genericKey, genericEvidence));
        }
        return result;
    }

    private static void allocate(int samples, int evidence, List<Bucket> buckets) {
        int allocated = 0;
        for (Bucket bucket : buckets) {
            long weighted = (long) samples * bucket.evidence;
            bucket.allocated = (int) (weighted / evidence);
            bucket.remainder = weighted % evidence;
            allocated += bucket.allocated;
        }
        buckets.stream().sorted((left, right) -> Long.compare(right.remainder, left.remainder))
                .limit(samples - allocated).forEach(bucket -> bucket.allocated++);
    }

    private static void recordAllocated(
            Map<TickSamples.TickableKey, PerfSampleAggregator.TickableAggregate> aggregates,
            List<Bucket> buckets,
            long tick) {
        for (Bucket bucket : buckets) {
            if (bucket.allocated <= 0) {
                continue;
            }
            aggregates.computeIfAbsent(bucket.key,
                    ignored -> new PerfSampleAggregator.TickableAggregate())
                    .recordSamples(bucket.allocated, tick);
        }
    }

    private static final class Bucket {
        final TickSamples.TickableKey key;
        final int evidence;
        int allocated;
        long remainder;

        Bucket(TickSamples.TickableKey key, int evidence) {
            this.key = key;
            this.evidence = evidence;
        }
    }
}
