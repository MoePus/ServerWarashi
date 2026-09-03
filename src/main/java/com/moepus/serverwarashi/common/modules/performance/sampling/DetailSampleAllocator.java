package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DetailSampleAllocator {
    private static final int MIN_EVIDENCE_SAMPLES = 2;
    private static final String GENERIC_UNKNOWN = "Unknown";

    private final Map<PerfWorkTracker.Snapshot, Integer> handlers = new LinkedHashMap<>();
    private final Map<PerfWorkTracker.Snapshot, Integer> dispatches = new LinkedHashMap<>();
    private final List<StackTraceElement[]> unknownStacks = new ArrayList<>();

    void record(AsyncStackSample sample, PrimaryObservationRing.CoarseState state) {
        PerfStackAttributor.Classification detail = PerfStackAttributor.classify(sample.stack());
        if (state == PrimaryObservationRing.CoarseState.HANDLER) {
            if (detail.kind() == PerfStackAttributor.Kind.HANDLER) {
                handlers.merge(detail.snapshot(), 1, Integer::sum);
            }
            return;
        }
        if (state != PrimaryObservationRing.CoarseState.UNKNOWN) {
            return;
        }
        if (detail.kind() == PerfStackAttributor.Kind.ENTITY_DISPATCH
                || detail.kind() == PerfStackAttributor.Kind.BLOCK_ENTITY_DISPATCH) {
            dispatches.merge(detail.snapshot(), 1, Integer::sum);
        } else if (detail.kind() == PerfStackAttributor.Kind.UNKNOWN) {
            unknownStacks.add(sample.stack());
        }
    }

    void apply(TickSamples tick, UnknownStackReservoir unknownReservoir) {
        splitUnknown(tick);
        if (tick.unknownSamples > 0L) {
            recordUnknownEvidence(unknownReservoir, tick.tickId);
        }
    }

    Map<PerfWorkTracker.Snapshot, Integer> handlerEvidence() {
        return handlers;
    }

    private void splitUnknown(TickSamples tick) {
        int evidence = evidenceCount(dispatches) + unknownStacks.size();
        if (evidence < MIN_EVIDENCE_SAMPLES) {
            return;
        }
        int coarseSamples = tick.removeGeneric(TickType.TASK, GENERIC_UNKNOWN);
        if (coarseSamples == 0) {
            return;
        }
        List<Bucket> buckets = buckets(dispatches);
        Bucket unknown = new Bucket(PerfWorkTracker.unknownSnapshot(), unknownStacks.size());
        buckets.add(unknown);
        allocate(coarseSamples, buckets);
        addDispatches(tick, buckets);
        tick.addDetail(unknown.snapshot, unknown.allocated,
                PerfReportData.AttributionSource.FALLBACK_UNKNOWN,
                PerfReportData.Confidence.LOW);
        tick.unknownSamples = unknown.allocated;
    }

    private static void addDispatches(TickSamples tick, List<Bucket> buckets) {
        for (int index = 0; index < buckets.size() - 1; index++) {
            Bucket bucket = buckets.get(index);
            tick.addDetail(bucket.snapshot, bucket.allocated,
                    PerfReportData.AttributionSource.ASYNC_STACK,
                    PerfReportData.Confidence.MEDIUM);
        }
    }

    private void recordUnknownEvidence(UnknownStackReservoir reservoir, long tickId) {
        for (StackTraceElement[] stack : unknownStacks) {
            reservoir.record(stack, tickId);
        }
    }

    private static void allocate(int samples, List<Bucket> buckets) {
        int evidence = buckets.stream().mapToInt(bucket -> bucket.evidence).sum();
        int allocated = 0;
        for (Bucket bucket : buckets) {
            long weighted = (long) samples * bucket.evidence;
            bucket.allocated = (int) (weighted / evidence);
            bucket.remainder = weighted % evidence;
            allocated += bucket.allocated;
        }
        buckets.stream()
                .sorted((left, right) -> Long.compare(right.remainder, left.remainder))
                .limit(samples - allocated)
                .forEach(bucket -> bucket.allocated++);
    }

    private static List<Bucket> buckets(Map<PerfWorkTracker.Snapshot, Integer> evidence) {
        List<Bucket> result = new ArrayList<>(evidence.size() + 1);
        evidence.forEach((snapshot, count) -> result.add(new Bucket(snapshot, count)));
        return result;
    }

    private static int evidenceCount(Map<PerfWorkTracker.Snapshot, Integer> evidence) {
        return evidence.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static final class Bucket {
        final PerfWorkTracker.Snapshot snapshot;
        final int evidence;
        int allocated;
        long remainder;

        Bucket(PerfWorkTracker.Snapshot snapshot, int evidence) {
            this.snapshot = snapshot;
            this.evidence = evidence;
        }
    }
}
