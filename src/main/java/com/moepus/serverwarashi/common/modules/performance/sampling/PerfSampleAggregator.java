package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

final class PerfSampleAggregator {
    private static final long GC_WINDOW_NANOS = 1_000_000_000L;
    private static final int CHUNK_VIEW_LIMIT_PER_METRIC = 256;
    private static final int TICKABLE_VIEW_LIMIT_PER_METRIC = 256;
    private static final int TRACKED_KEY_LIMIT = 16_384;
    private static final int DROPPED_HASH_LIMIT = 65_536;
    private static final int REPLACEMENT_INTERVAL = 1_024;
    private static final Comparator<PerfReportData.ChunkEntry> STABLE_CHUNK_ORDER = Comparator
            .comparing(PerfReportData.ChunkEntry::dimension)
            .thenComparingInt(PerfReportData.ChunkEntry::chunkX)
            .thenComparingInt(PerfReportData.ChunkEntry::chunkZ);
    private static final Comparator<PerfReportData.TickableEntry> STABLE_TICKABLE_ORDER = Comparator
            .comparingInt((PerfReportData.TickableEntry entry) -> entry.type().ordinal())
            .thenComparing(PerfReportData.TickableEntry::dimension)
            .thenComparing(PerfReportData.TickableEntry::className)
            .thenComparing(PerfReportData.TickableEntry::detailId)
            .thenComparingInt(entry -> entry.detailKind().ordinal())
            .thenComparingInt(PerfReportData.TickableEntry::entityId)
            .thenComparingInt(PerfReportData.TickableEntry::x)
            .thenComparingInt(PerfReportData.TickableEntry::y)
            .thenComparingInt(PerfReportData.TickableEntry::z)
            .thenComparing(PerfReportData.TickableEntry::eventLoop)
            .thenComparingInt(entry -> entry.attributionSource().ordinal());
    private final long reportId;
    private final PerfOwnerAggregator ownerAggregator = new PerfOwnerAggregator();
    private final Map<TickSamples.TickableKey, TickableAggregate> tickables = new HashMap<>();
    private final Map<TickSamples.ChunkKey, SampleStatistics> chunks = new HashMap<>();
    private final ArrayDeque<GcTick> recentGcTicks = new ArrayDeque<>();
    private final IntOpenHashSet droppedTickableHashes = new IntOpenHashSet();
    private final IntOpenHashSet droppedChunkHashes = new IntOpenHashSet();
    private int tickableDropsUntilReplacement = REPLACEMENT_INTERVAL;
    private int chunkDropsUntilReplacement = REPLACEMENT_INTERVAL;
    private long tickCount;
    private long hotspotRevision;
    private long totalTickNanos;
    private long recentGcTotalMillis;

    PerfSampleAggregator(long seed) {
        reportId = seed;
    }

    PerfReportData buildLoaderLayoutOnly(PerfAttributionSnapshot attribution, int intervalMicros) {
        PerfOwnerAggregator.LoaderBuild loaders = PerfOwnerAggregator.loaderSources(attribution);
        return new PerfReportData(reportId, 0.0D, 0L, System.currentTimeMillis(),
                0L, 0L, intervalMicros, 0, 0, 0,
                loaders.omittedSources(), List.of(), List.of(), List.of(), loaders.sources());
    }

    void accept(TickSamples tick) {
        tickCount++;
        totalTickNanos += tick.durationNanos;
        recordGc(tick);
        if (tick.totalSamples == 0) {
            ownerAggregator.updateMetadata(tick.attribution);
        } else {
            recordTickables(tick);
            recordChunks(tick);
            ownerAggregator.accept(tick, tickCount);
        }
    }

    PerfReportData build(long elapsedNanos, long totalSamples, int intervalMicros) {
        return buildSnapshot(elapsedNanos, totalSamples, intervalMicros).report();
    }

    BuildSnapshot buildSnapshot(long elapsedNanos, long totalSamples, int intervalMicros) {
        double nanosPerSample = SampleDensity.nanosPerAttempt(
                elapsedNanos, totalSamples, intervalMicros);
        SampleMetricContext metricContext = new SampleMetricContext(
                tickCount, tickCount, nanosPerSample, intervalMicros * 1_000.0D);
        PerfObjectSnapshotBuilder.Build objectBuild =
                PerfObjectSnapshotBuilder.build(tickables, metricContext);
        var limitedChunks = PerfReportLimits.topMetricUnion(
                chunkEntries(objectBuild.chunkCounts(), nanosPerSample), CHUNK_VIEW_LIMIT_PER_METRIC,
                PerfReportData.ChunkEntry::metric, STABLE_CHUNK_ORDER);
        var sampledTickables = objectBuild.tickables();
        var limitedTickables = PerfReportLimits.topMetricUnion(
                sampledTickables.entries(), TICKABLE_VIEW_LIMIT_PER_METRIC,
                PerfReportData.TickableEntry::metric, STABLE_TICKABLE_ORDER);
        PerfOwnerAggregator.Build ownerBuild = ownerAggregator.build(tickCount, nanosPerSample);
        GcMetric gc = gcMetric();
        List<PerfReportData.TickableEntry> reportTickables = withGcTickable(limitedTickables.entries(), gc);
        double serverMspt = tickCount == 0 ? 0.0D : totalTickNanos / 1_000_000.0D / tickCount;
        PerfReportData report = new PerfReportData(
                reportId, serverMspt, elapsedNanos / 1_000_000L, System.currentTimeMillis(),
                tickCount, totalSamples, intervalMicros,
                omitted(limitedChunks.omitted(), droppedChunkHashes),
                omitted((long) sampledTickables.omitted() + limitedTickables.omitted(), droppedTickableHashes),
                ownerBuild.omitted(), ownerBuild.omittedLoaderSources(),
                limitedChunks.entries(), reportTickables, ownerBuild.entries(), ownerBuild.loaderSources());
        PerfWorldHotspotIndex hotspots = new PerfWorldHotspotIndex(
                reportId, ++hotspotRevision, tickCount, Math.max(0L, Math.round(nanosPerSample)),
                objectBuild.hotspots());
        return new BuildSnapshot(report, hotspots);
    }

    private void recordGc(TickSamples tick) {
        long cutoff = tick.completedAtNanos - GC_WINDOW_NANOS;
        while (!recentGcTicks.isEmpty() && recentGcTicks.getFirst().completedAtNanos() < cutoff) {
            recentGcTotalMillis -= recentGcTicks.removeFirst().gcMillis();
        }
        recentGcTicks.addLast(new GcTick(tick.completedAtNanos, tick.gcTimeMillis));
        recentGcTotalMillis += tick.gcTimeMillis;
    }

    private GcMetric gcMetric() {
        if (recentGcTicks.isEmpty()) {
            return new GcMetric(0.0D, 0L, 0L);
        }
        long maximum = 0L;
        for (GcTick tick : recentGcTicks) {
            maximum = Math.max(maximum, tick.gcMillis());
        }
        return new GcMetric((double) recentGcTotalMillis / recentGcTicks.size(), maximum, recentGcTicks.size());
    }

    private static List<PerfReportData.TickableEntry> withGcTickable(
            List<PerfReportData.TickableEntry> entries, GcMetric gc) {
        if (gc.ticks() == 0L) {
            return entries;
        }
        List<PerfReportData.TickableEntry> result = new ArrayList<>(entries.size() + 1);
        result.addAll(entries);
        result.add(new PerfReportData.TickableEntry(TickType.GC, "", "Rolling 1s", "",
                TickDetailKind.NONE, -1, false, 0, 0, 0, false, "",
                PerfReportData.AttributionSource.JVM_METRIC, PerfReportData.Confidence.HIGH,
                new PerfReportData.Metric(gc.averageMillis(), gc.maxMillis(), gc.ticks())));
        return List.copyOf(result);
    }

    private void recordTickables(TickSamples tick) {
        for (var entry : tick.tickables.entrySet()) {
            TickSamples.TickCount count = entry.getValue();
            TickableAggregate aggregate = tickableAggregate(entry.getKey());
            if (aggregate != null) {
                aggregate.record(count, tickCount);
            }
        }
    }

    private void recordChunks(TickSamples tick) {
        for (var entry : tick.chunks.entrySet()) {
            int count = entry.getValue();
            SampleStatistics statistics = chunkStatistics(entry.getKey());
            if (statistics != null) {
                statistics.record(count, tickCount);
            }
        }
    }

    private TickableAggregate tickableAggregate(TickSamples.TickableKey key) {
        TickableAggregate existing = tickables.get(key);
        if (existing != null || tickables.size() < TRACKED_KEY_LIMIT) {
            return existing != null ? existing : addTickable(key);
        }
        recordDropped(droppedTickableHashes, key);
        if (--tickableDropsUntilReplacement > 0) {
            return null;
        }
        tickableDropsUntilReplacement = REPLACEMENT_INTERVAL;
        TickSamples.TickableKey evicted = evictLightest(tickables, TickableAggregate::totalWeight);
        recordDropped(droppedTickableHashes, evicted);
        return addTickable(key);
    }

    private TickableAggregate addTickable(TickSamples.TickableKey key) {
        TickableAggregate aggregate = new TickableAggregate();
        tickables.put(key, aggregate);
        return aggregate;
    }

    private SampleStatistics chunkStatistics(TickSamples.ChunkKey key) {
        SampleStatistics existing = chunks.get(key);
        if (existing != null || chunks.size() < TRACKED_KEY_LIMIT) {
            return existing != null ? existing : addStatistics(chunks, key);
        }
        recordDropped(droppedChunkHashes, key);
        if (--chunkDropsUntilReplacement > 0) {
            return null;
        }
        chunkDropsUntilReplacement = REPLACEMENT_INTERVAL;
        recordDropped(droppedChunkHashes, evictLightest(chunks, SampleStatistics::totalWeight));
        return addStatistics(chunks, key);
    }

    private static <K> SampleStatistics addStatistics(Map<K, SampleStatistics> map, K key) {
        SampleStatistics statistics = new SampleStatistics();
        map.put(key, statistics);
        return statistics;
    }

    private static <K, V> K evictLightest(Map<K, V> map, ToDoubleFunction<V> weight) {
        K lightest = null;
        double lightestWeight = Double.POSITIVE_INFINITY;
        for (var entry : map.entrySet()) {
            double candidate = weight.applyAsDouble(entry.getValue());
            if (candidate < lightestWeight) {
                lightest = entry.getKey();
                lightestWeight = candidate;
            }
        }
        if (lightest != null) {
            map.remove(lightest);
        }
        return lightest;
    }

    private static void recordDropped(IntOpenHashSet hashes, Object key) {
        if (key != null && hashes.size() < DROPPED_HASH_LIMIT) {
            hashes.add(key.hashCode());
        }
    }

    private static int omitted(long limited, IntOpenHashSet droppedHashes) {
        return (int) Math.min(Integer.MAX_VALUE, limited + droppedHashes.size());
    }

    private List<PerfReportData.ChunkEntry> chunkEntries(
            Map<TickSamples.ChunkKey, PerfObjectSnapshotBuilder.ObjectCounts> objectCounts,
            double nanosPerSample) {
        List<PerfReportData.ChunkEntry> result = new ArrayList<>(chunks.size());
        chunks.forEach((key, stats) -> {
            ChunkPos pos = new ChunkPos(key.position());
            PerfObjectSnapshotBuilder.ObjectCounts counts = objectCounts.getOrDefault(
                    key, PerfObjectSnapshotBuilder.ObjectCounts.EMPTY);
            result.add(new PerfReportData.ChunkEntry(key.dimension(), pos.x, pos.z,
                    counts.count(TickType.ENTITY), counts.count(TickType.BLOCK_ENTITY),
                    counts.top(TickType.ENTITY),
                    counts.top(TickType.BLOCK_ENTITY), stats.metric(tickCount, nanosPerSample)));
        });
        return result;
    }

    static final class TickableAggregate {
        final SampleStatistics stats = new SampleStatistics();
        boolean positioned;
        boolean chunkPosition;
        int x, y, z;
        long lastSeenTick;
        long measurementStartTick = 1L;

        void record(TickSamples.TickCount count, long tick) {
            positioned = count.positioned;
            chunkPosition = count.chunkPosition;
            x = count.x;
            y = count.y;
            z = count.z;
            lastSeenTick = tick;
            if (count.entityAgeTicks >= 0) {
                long age = Math.max(1L, count.entityAgeTicks);
                measurementStartTick = Math.max(1L, tick - age + 1L);
            }
            stats.record(count.count, tick);
        }

        double totalWeight() {
            return stats.totalWeight();
        }

        PerfReportData.TickableEntry toReport(
                TickSamples.TickableKey key,
                SampleMetricContext context) {
            return toReport(key, metric(key, context));
        }

        PerfReportData.TickableEntry toReport(TickSamples.TickableKey key,
                                              PerfReportData.Metric metric) {
            String displayName = key.displayName().isEmpty() ? className(key.workClass()) : key.displayName();
            return new PerfReportData.TickableEntry(key.type(), key.dimension(), displayName,
                    nullableId(key.detailId()), key.detailKind(), key.entityId(), positioned,
                    x, y, z, chunkPosition, key.eventLoop(),
                    key.attributionSource(), key.confidence(),
                    metric);
        }

        PerfReportData.Metric metric(TickSamples.TickableKey key,
                                     SampleMetricContext context) {
            return stats.metric(key.metricMode(),
                    context.withMeasurementTicks(measurementTicks(context.currentTick())));
        }

        long measurementTicks(long ticks) {
            return ticks <= 0L ? 0L : Math.max(1L, ticks - measurementStartTick + 1L);
        }

        TickSamples.ChunkKey chunkKey(TickSamples.TickableKey key) {
            if (!positioned || (key.type() != TickType.ENTITY && key.type() != TickType.BLOCK_ENTITY)) {
                return null;
            }
            return new TickSamples.ChunkKey(key.dimension(), ChunkPos.asLong(x >> 4, z >> 4));
        }

        static String className(Class<?> type) {
            String simple = type.getSimpleName();
            if (!simple.isEmpty()) {
                return simple;
            }
            String name = type.getName();
            int packageEnd = name.lastIndexOf('.');
            int hiddenSuffix = name.indexOf('/');
            int end = hiddenSuffix < 0 ? name.length() : hiddenSuffix;
            return name.substring(packageEnd + 1, end);
        }

        static String nullableId(ResourceLocation id) {
            return id == null ? "" : id.toString();
        }
    }

    record BuildSnapshot(PerfReportData report, PerfWorldHotspotIndex hotspots) {
    }

    private record GcTick(long completedAtNanos, long gcMillis) {
    }

    private record GcMetric(double averageMillis, long maxMillis, long ticks) {
    }

}
