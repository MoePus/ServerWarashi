package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

import java.util.List;

final class PerfSamplerReportSink {
    private static final int REPORT_INTERVAL_TICKS = 20;
    private static final int TICKABLE_ENTRY_KIND = 1;
    private static final int CHUNK_ENTRY_KIND = 1 << 1;

    private final PerfSampleAggregator aggregator;
    private final UnknownStackReservoir unknownStacks;
    private final int intervalMicros;
    private volatile PerfSamplerSession.PublishedSnapshot latest;
    private long acceptedTicks;
    private int publishedEntryKinds;

    PerfSamplerReportSink(long reportId,
                          int intervalMicros,
                          PerfAttributionSnapshot initialAttribution) {
        this.intervalMicros = intervalMicros;
        aggregator = new PerfSampleAggregator(reportId);
        unknownStacks = new UnknownStackReservoir(reportId ^ 0x6A09E667F3BCC909L);
        PerfReportData initial = initialAttribution == null
                ? PerfReportData.empty(intervalMicros)
                : aggregator.buildLoaderLayoutOnly(initialAttribution, intervalMicros);
        latest = new PerfSamplerSession.PublishedSnapshot(initial,
                PerfWorldHotspotIndex.empty(initial.reportId(), 0L, 0L));
    }

    synchronized void accept(List<TickSamples> ticks, BuildContext context) {
        for (TickSamples tick : ticks) {
            acceptTick(tick, context);
        }
    }

    synchronized void flush(List<TickSamples> ticks, BuildContext context) {
        accept(ticks, context);
        if (acceptedTicks > 0L) {
            latest = build(context);
        }
    }

    synchronized void finish(List<TickSamples> ticks,
                             BuildContext context,
                             boolean retainInitialLayout) {
        accept(ticks, context);
        if (acceptedTicks > 0L) {
            latest = build(context);
        } else if (!retainInitialLayout) {
            PerfReportData empty = PerfReportData.empty(intervalMicros);
            latest = new PerfSamplerSession.PublishedSnapshot(empty,
                    PerfWorldHotspotIndex.empty(0L, 0L, 0L));
        }
    }

    PerfSamplerSession.PublishedSnapshot latest() {
        return latest;
    }

    synchronized UnknownStackSnapshot unknownStackSnapshot(long reportId,
                                                           long completedTickId,
                                                           boolean stopped) {
        var parameters = new UnknownStackReservoir.SnapshotParameters(
                reportId, completedTickId, intervalMicros, completedTickId,
                0L, stopped);
        return unknownStacks.snapshot(parameters);
    }

    private void acceptTick(TickSamples tick, BuildContext context) {
        tick.applyDetails(unknownStacks);
        unknownStacks.completeTick(tick.tickId, tick.unknownSamples);
        aggregator.accept(tick);
        acceptedTicks++;
        int kinds = entryKinds(tick);
        boolean firstKind = (kinds & ~publishedEntryKinds) != 0;
        if (shouldPublishReport(acceptedTicks, tick.refreshReport, firstKind)) {
            latest = build(context);
        }
        publishedEntryKinds |= kinds;
    }

    private PerfSamplerSession.PublishedSnapshot build(BuildContext context) {
        PerfSampleAggregator.BuildSnapshot build = aggregator.buildSnapshot(
                context.elapsedNanos, context.sampleAttempts, intervalMicros);
        return new PerfSamplerSession.PublishedSnapshot(
                build.report().withSampling(context.metadata), build.hotspots());
    }

    static boolean shouldPublishReport(long acceptedTicks,
                                       boolean refreshRequested,
                                       boolean firstEntryKind) {
        return acceptedTicks % REPORT_INTERVAL_TICKS == 0L
                || refreshRequested || firstEntryKind;
    }

    private static int entryKinds(TickSamples tick) {
        int kinds = tick.tickables.isEmpty() ? 0 : TICKABLE_ENTRY_KIND;
        return tick.chunks.isEmpty() ? kinds : kinds | CHUNK_ENTRY_KIND;
    }

    record BuildContext(long elapsedNanos,
                        long sampleAttempts,
                        PerfReportData.SamplingMetadata metadata) {
    }
}
