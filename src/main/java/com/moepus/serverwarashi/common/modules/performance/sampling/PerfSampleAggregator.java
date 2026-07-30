package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;

final class PerfSampleAggregator {
    private static final int VIEW_LIMIT = 512;
    private static final int MAX_COORDINATES_PER_GROUP = 16;
    private static final int TRACKED_KEY_LIMIT = 16_384;
    private static final int DROPPED_HASH_LIMIT = 65_536;
    private static final int REPLACEMENT_INTERVAL = 1_024;
    private final SplittableRandom random;
    private final PerfOwnerAggregator ownerAggregator = new PerfOwnerAggregator();
    private final Map<TickSamples.TickableKey, TickableAggregate> tickables = new HashMap<>();
    private final Map<CoordinateGroup, CoordinateReservoir> coordinateGroups = new HashMap<>();
    private final Map<TickSamples.ChunkKey, SampleStatistics> chunks = new HashMap<>();
    private final IntOpenHashSet droppedTickableHashes = new IntOpenHashSet();
    private final IntOpenHashSet droppedChunkHashes = new IntOpenHashSet();
    private int tickableDropsUntilReplacement = REPLACEMENT_INTERVAL;
    private int chunkDropsUntilReplacement = REPLACEMENT_INTERVAL;
    private long tickCount;
    private long totalSamples;
    private long totalTickNanos;

    PerfSampleAggregator(long seed) {
        random = new SplittableRandom(seed);
    }

    PerfReportData buildLoaderLayoutOnly(PerfAttributionSnapshot attribution, int intervalMicros) {
        PerfOwnerAggregator.LoaderBuild loaders = PerfOwnerAggregator.loaderSources(attribution);
        return new PerfReportData(0.0D, 0L, System.currentTimeMillis(),
                0L, 0L, intervalMicros, 0, 0, 0,
                loaders.omittedSources(), List.of(), List.of(), List.of(), loaders.sources());
    }

    void accept(TickSamples tick) {
        tickCount++;
        totalSamples += tick.totalSamples;
        totalTickNanos += tick.durationNanos;
        if (tick.totalSamples == 0) {
            ownerAggregator.updateMetadata(tick.attribution);
        } else {
            recordTickables(tick);
            recordChunks(tick);
            ownerAggregator.accept(tick, tickCount);
        }
    }

    PerfReportData build(long elapsedMillis, int intervalMicros) {
        ObjectBuild objectBuild = buildObjects();
        var limitedChunks = PerfReportLimits.topMetricUnion(chunkEntries(objectBuild.chunkCounts()), VIEW_LIMIT,
                PerfReportData.ChunkEntry::metric);
        var sampledTickables = objectBuild.tickables();
        var limitedTickables = PerfReportLimits.topMetricUnion(sampledTickables.entries(), VIEW_LIMIT,
                PerfReportData.TickableEntry::metric);
        PerfOwnerAggregator.Build ownerBuild = ownerAggregator.build(tickCount);
        double serverMspt = tickCount == 0 ? 0.0D : totalTickNanos / 1_000_000.0D / tickCount;
        return new PerfReportData(serverMspt, elapsedMillis, System.currentTimeMillis(),
                tickCount, totalSamples, intervalMicros,
                omitted(limitedChunks.omitted(), droppedChunkHashes),
                omitted((long) sampledTickables.omitted() + limitedTickables.omitted(), droppedTickableHashes),
                ownerBuild.omitted(), ownerBuild.omittedLoaderSources(),
                limitedChunks.entries(), limitedTickables.entries(), ownerBuild.entries(), ownerBuild.loaderSources());
    }

    private void recordTickables(TickSamples tick) {
        for (var entry : tick.tickables.entrySet()) {
            TickSamples.TickCount count = entry.getValue();
            double nanos = estimate(tick.durationNanos, count.count, tick.totalSamples);
            TickableAggregate aggregate = tickableAggregate(entry.getKey());
            if (aggregate != null) {
                aggregate.record(count, nanos, tickCount);
            }
        }
    }

    private void recordChunks(TickSamples tick) {
        for (var entry : tick.chunks.entrySet()) {
            int count = entry.getValue();
            double nanos = estimate(tick.durationNanos, count, tick.totalSamples);
            SampleStatistics statistics = chunkStatistics(entry.getKey());
            if (statistics != null) {
                statistics.record(nanos, count, tickCount);
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
        TickSamples.TickableKey evicted = evictLightest(tickables, TickableAggregate::totalNanos);
        forgetCoordinate(evicted);
        recordDropped(droppedTickableHashes, evicted);
        return addTickable(key);
    }

    private TickableAggregate addTickable(TickSamples.TickableKey key) {
        TickableAggregate aggregate = new TickableAggregate();
        tickables.put(key, aggregate);
        if (isObject(key.type())) {
            coordinateGroups.computeIfAbsent(CoordinateGroup.from(key), ignored -> new CoordinateReservoir())
                    .offer(key, random);
        }
        return aggregate;
    }

    private void forgetCoordinate(TickSamples.TickableKey key) {
        if (key == null || !isObject(key.type())) {
            return;
        }
        CoordinateGroup group = CoordinateGroup.from(key);
        CoordinateReservoir reservoir = coordinateGroups.get(group);
        if (reservoir == null || !reservoir.remove(key)) {
            return;
        }
        refillCoordinate(group, reservoir);
        if (reservoir.isEmpty()) {
            coordinateGroups.remove(group);
        }
    }

    private void refillCoordinate(CoordinateGroup group, CoordinateReservoir reservoir) {
        for (TickSamples.TickableKey candidate : tickables.keySet()) {
            if (group.equals(CoordinateGroup.from(candidate)) && !reservoir.contains(candidate)) {
                reservoir.offer(candidate, random);
                return;
            }
        }
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
        recordDropped(droppedChunkHashes, evictLightest(chunks, SampleStatistics::totalNanos));
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

    private List<PerfReportData.ChunkEntry> chunkEntries(Map<TickSamples.ChunkKey, ObjectCounts> objectCounts) {
        List<PerfReportData.ChunkEntry> result = new ArrayList<>(chunks.size());
        chunks.forEach((key, stats) -> {
            ChunkPos pos = new ChunkPos(key.position());
            ObjectCounts counts = objectCounts.getOrDefault(key, ObjectCounts.EMPTY);
            result.add(new PerfReportData.ChunkEntry(key.dimension(), pos.x, pos.z,
                    counts.entities, counts.blockEntities, counts.top(TickType.ENTITY),
                    counts.top(TickType.BLOCK_ENTITY), stats.metric(tickCount)));
        });
        return result;
    }

    private ObjectBuild buildObjects() {
        Map<TickSamples.ChunkKey, ObjectCounts> chunkCounts = new HashMap<>();
        List<PerfReportData.TickableEntry> entries = new ArrayList<>();
        tickables.forEach((key, aggregate) -> {
            TickSamples.ChunkKey chunk = aggregate.chunkKey(key);
            if (chunk != null) {
                chunkCounts.computeIfAbsent(chunk, ignored -> new ObjectCounts()).add(key, aggregate);
            }
            if (isRepresentative(key)) {
                entries.add(aggregate.toReport(key, tickCount));
            }
        });
        var limited = new PerfReportLimits.Limited<>(List.copyOf(entries), tickables.size() - entries.size());
        return new ObjectBuild(chunkCounts, limited);
    }

    private boolean isRepresentative(TickSamples.TickableKey key) {
        if (!isObject(key.type())) {
            return true;
        }
        CoordinateReservoir reservoir = coordinateGroups.get(CoordinateGroup.from(key));
        return reservoir != null && reservoir.contains(key);
    }

    private static boolean isObject(TickType type) {
        return type == TickType.ENTITY || type == TickType.BLOCK_ENTITY;
    }

    private static double estimate(long durationNanos, int samples, int totalSamples) {
        return durationNanos * (samples / (double) totalSamples);
    }

    private static final class TickableAggregate {
        final SampleStatistics stats = new SampleStatistics();
        boolean positioned;
        boolean chunkPosition;
        int x, y, z;

        void record(TickSamples.TickCount count, double nanos, long tick) {
            positioned = count.positioned;
            chunkPosition = count.chunkPosition;
            x = count.x;
            y = count.y;
            z = count.z;
            stats.record(nanos, count.count, tick);
        }

        double totalNanos() {
            return stats.totalNanos();
        }

        PerfReportData.TickableEntry toReport(TickSamples.TickableKey key, long ticks) {
            return new PerfReportData.TickableEntry(key.type(), key.dimension(), className(key.workClass()),
                    nullableId(key.detailId()), key.detailKind(), key.entityId(), positioned,
                    x, y, z, chunkPosition, key.eventLoop(),
                    stats.metric(ticks));
        }

        TickSamples.ChunkKey chunkKey(TickSamples.TickableKey key) {
            if (!positioned || (key.type() != TickType.ENTITY && key.type() != TickType.BLOCK_ENTITY)) {
                return null;
            }
            return new TickSamples.ChunkKey(key.dimension(), ChunkPos.asLong(x >> 4, z >> 4));
        }

        private static String className(Class<?> type) {
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

        private static String nullableId(ResourceLocation id) {
            return id == null ? "" : id.toString();
        }
    }

    private record ObjectBuild(
            Map<TickSamples.ChunkKey, ObjectCounts> chunkCounts,
            PerfReportLimits.Limited<PerfReportData.TickableEntry> tickables) {
    }

    private static final class ObjectCounts {
        private static final int TOP_LIMIT = 3;
        static final ObjectCounts EMPTY = new ObjectCounts();
        final Map<ObjectType, ObjectSummary> types = new HashMap<>();
        int entities;
        int blockEntities;

        void add(TickSamples.TickableKey key, TickableAggregate aggregate) {
            if (key.type() == TickType.ENTITY) {
                entities++;
            } else if (key.type() == TickType.BLOCK_ENTITY) {
                blockEntities++;
            } else {
                return;
            }
            types.computeIfAbsent(new ObjectType(
                            key.type(), key.workClass(), key.detailId(), key.detailKind()),
                    ignored -> new ObjectSummary()).add(aggregate);
        }

        List<PerfReportData.ObjectCount> top(TickType type) {
            return types.entrySet().stream()
                    .filter(entry -> entry.getKey().type == type)
                    .sorted(Comparator.<Map.Entry<ObjectType, ObjectSummary>>comparingInt(
                                    entry -> entry.getValue().count)
                            .reversed()
                            .thenComparing(entry -> entry.getKey().workClass.getName())
                            .thenComparing(entry -> String.valueOf(entry.getKey().detailId)))
                    .limit(TOP_LIMIT)
                    .map(entry -> new PerfReportData.ObjectCount(
                            TickableAggregate.className(entry.getKey().workClass),
                            TickableAggregate.nullableId(entry.getKey().detailId),
                            entry.getKey().detailKind,
                            entry.getValue().count, entry.getValue().x, entry.getValue().y, entry.getValue().z))
                    .toList();
        }
    }
    private static final class ObjectSummary {
        int count;
        double representativeWeight = -1.0D;
        int x;
        int y;
        int z;

        void add(TickableAggregate aggregate) {
            count++;
            double weight = aggregate.stats.sampleWeight();
            if (weight > representativeWeight) {
                representativeWeight = weight;
                x = aggregate.x;
                y = aggregate.y;
                z = aggregate.z;
            }
        }
    }
    private record ObjectType(TickType type, Class<?> workClass, ResourceLocation detailId,
                              TickDetailKind detailKind) {
    }

    private record CoordinateGroup(TickType type, String dimension, Class<?> workClass,
                                   ResourceLocation detailId, TickDetailKind detailKind) {
        static CoordinateGroup from(TickSamples.TickableKey key) {
            return new CoordinateGroup(key.type(), key.dimension(), key.workClass(),
                    key.detailId(), key.detailKind());
        }
    }

    private static final class CoordinateReservoir {
        private final List<TickSamples.TickableKey> keys = new ArrayList<>(MAX_COORDINATES_PER_GROUP);
        private long observed;

        void offer(TickSamples.TickableKey key, SplittableRandom random) {
            observed++;
            if (keys.size() < MAX_COORDINATES_PER_GROUP) {
                keys.add(key);
                return;
            }
            long slot = random.nextLong(observed);
            if (slot < MAX_COORDINATES_PER_GROUP) {
                keys.set((int) slot, key);
            }
        }

        boolean contains(TickSamples.TickableKey key) {
            return keys.contains(key);
        }

        boolean remove(TickSamples.TickableKey key) {
            return keys.remove(key);
        }

        boolean isEmpty() {
            return keys.isEmpty();
        }
    }
}
