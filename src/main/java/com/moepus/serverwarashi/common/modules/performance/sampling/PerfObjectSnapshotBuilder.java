package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.ObjectPerformanceScale;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class PerfObjectSnapshotBuilder {
    private static final int MAX_COORDINATES_PER_GROUP = 32;
    private static final Comparator<StableObjectId> STABLE_OBJECT_ORDER = Comparator
            .comparingInt(StableObjectId::type)
            .thenComparing(StableObjectId::dimension)
            .thenComparing(StableObjectId::className)
            .thenComparing(StableObjectId::detailId)
            .thenComparingInt(StableObjectId::detailKind)
            .thenComparingInt(StableObjectId::entityId)
            .thenComparingLong(StableObjectId::fixedPosition)
            .thenComparingInt(StableObjectId::x)
            .thenComparingInt(StableObjectId::y)
            .thenComparingInt(StableObjectId::z)
            .thenComparing(StableObjectId::eventLoop)
            .thenComparing(StableObjectId::displayName);
    private static final Comparator<ObjectCandidate> HOTTEST_FIRST = Comparator
            .comparingDouble(ObjectCandidate::effectiveSamples).reversed()
            .thenComparing(Comparator.comparingDouble(
                    (ObjectCandidate candidate) -> candidate.metric().mspt()).reversed())
            .thenComparing(Comparator.comparingDouble(
                    (ObjectCandidate candidate) -> candidate.metric().max()).reversed())
            .thenComparing(ObjectCandidate::stableId, STABLE_OBJECT_ORDER);
    private static final Comparator<HotspotIdentity> HOTSPOT_ORDER = Comparator
            .comparing(HotspotIdentity::dimension)
            .thenComparingInt(identity -> identity.key().kind().ordinal())
            .thenComparingInt(identity -> identity.key().entityId())
            .thenComparingLong(identity -> identity.key().blockPosition());

    private PerfObjectSnapshotBuilder() {
    }

    static Build build(Map<TickSamples.TickableKey, PerfSampleAggregator.TickableAggregate> tickables,
                       long tickCount,
                       double nanosPerSample) {
        Builder builder = new Builder(tickCount, nanosPerSample);
        tickables.forEach(builder::add);
        return builder.finish(tickables.size());
    }

    private static void offerHotCandidate(PriorityQueue<ObjectCandidate> group,
                                          ObjectCandidate candidate) {
        if (group.size() < MAX_COORDINATES_PER_GROUP) {
            group.add(candidate);
        } else if (HOTTEST_FIRST.compare(candidate, group.element()) < 0) {
            group.remove();
            group.add(candidate);
        }
    }

    private static boolean isObject(TickType type) {
        return type == TickType.ENTITY || type == TickType.BLOCK_ENTITY;
    }

    private static final class Builder {
        private final long tickCount;
        private final double nanosPerSample;
        private final Map<TickSamples.ChunkKey, ObjectCounts> chunkCounts = new HashMap<>();
        private final Map<CoordinateGroup, PriorityQueue<ObjectCandidate>> groups = new HashMap<>();
        private final Map<HotspotIdentity, ObjectCandidate> hotspots = new HashMap<>();
        private final List<PerfReportData.TickableEntry> reportEntries = new ArrayList<>();

        private Builder(long tickCount, double nanosPerSample) {
            this.tickCount = tickCount;
            this.nanosPerSample = nanosPerSample;
        }

        void add(TickSamples.TickableKey key,
                 PerfSampleAggregator.TickableAggregate aggregate) {
            TickSamples.ChunkKey chunk = aggregate.chunkKey(key);
            if (chunk != null) {
                chunkCounts.computeIfAbsent(chunk, ignored -> new ObjectCounts()).add(key, aggregate);
            }
            if (!isObject(key.type())) {
                reportEntries.add(aggregate.toReport(key, tickCount, nanosPerSample));
                return;
            }
            ObjectCandidate candidate = ObjectCandidate.create(
                    key, aggregate, aggregate.metric(tickCount, nanosPerSample), tickCount);
            PriorityQueue<ObjectCandidate> group = groups.computeIfAbsent(
                    CoordinateGroup.from(key), ignored -> new PriorityQueue<>(
                            MAX_COORDINATES_PER_GROUP, HOTTEST_FIRST.reversed()));
            offerHotCandidate(group, candidate);
            if (candidate.canCreateHotspot()) {
                HotspotIdentity identity = HotspotIdentity.from(key, candidate.hotspotKey());
                hotspots.merge(identity, candidate, ObjectCandidate::newer);
            }
        }

        Build finish(int trackedCount) {
            groups.values().stream().flatMap(group -> group.stream())
                    .sorted(HOTTEST_FIRST)
                    .map(ObjectCandidate::toReport)
                    .forEachOrdered(reportEntries::add);
            var limited = new PerfReportLimits.Limited<>(List.copyOf(reportEntries),
                    trackedCount - reportEntries.size());
            List<PerfWorldHotspotIndex.Entry> hotspotEntries = hotspots.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(HOTSPOT_ORDER))
                    .map(entry -> entry.getValue().toHotspot())
                    .toList();
            return new Build(chunkCounts, limited, hotspotEntries);
        }
    }

    record Build(Map<TickSamples.ChunkKey, ObjectCounts> chunkCounts,
                 PerfReportLimits.Limited<PerfReportData.TickableEntry> tickables,
                 List<PerfWorldHotspotIndex.Entry> hotspots) {
    }

    static final class ObjectCounts {
        private static final int TOP_LIMIT = 3;
        static final ObjectCounts EMPTY = new ObjectCounts();
        private final Map<ObjectType, ObjectSummary> types = new HashMap<>();
        private int entities;
        private int blockEntities;

        void add(TickSamples.TickableKey key, PerfSampleAggregator.TickableAggregate aggregate) {
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

        int count(TickType type) {
            return type == TickType.ENTITY ? entities : blockEntities;
        }

        List<PerfReportData.ObjectCount> top(TickType type) {
            return types.entrySet().stream()
                    .filter(entry -> entry.getKey().type == type)
                    .sorted(Comparator.<Map.Entry<ObjectType, ObjectSummary>>comparingInt(
                                    entry -> entry.getValue().count).reversed()
                            .thenComparing(entry -> entry.getKey().workClass.getName())
                            .thenComparing(entry -> String.valueOf(entry.getKey().detailId)))
                    .limit(TOP_LIMIT)
                    .map(entry -> objectCount(entry.getKey(), entry.getValue()))
                    .toList();
        }

        private static PerfReportData.ObjectCount objectCount(
                ObjectType type, ObjectSummary summary) {
            return new PerfReportData.ObjectCount(
                    PerfSampleAggregator.TickableAggregate.className(type.workClass),
                    PerfSampleAggregator.TickableAggregate.nullableId(type.detailId),
                    type.detailKind, summary.count, summary.x, summary.y, summary.z);
        }
    }

    private record ObjectCandidate(TickSamples.TickableKey key,
                                   PerfSampleAggregator.TickableAggregate aggregate,
                                   PerfReportData.Metric metric,
                                   double effectiveSamples,
                                   StableObjectId stableId,
                                   long tickCount) {
        static ObjectCandidate create(TickSamples.TickableKey key,
                                      PerfSampleAggregator.TickableAggregate aggregate,
                                      PerfReportData.Metric metric,
                                      long tickCount) {
            return new ObjectCandidate(key, aggregate, metric,
                    ObjectPerformanceScale.effectiveSamples(metric, tickCount),
                    StableObjectId.from(key, aggregate), tickCount);
        }

        PerfReportData.TickableEntry toReport() {
            return aggregate.toReport(key, metric);
        }

        boolean canCreateHotspot() {
            return aggregate.positioned && hotspotKey() != null && key.detailId() != null
                    && !key.dimension().isEmpty() && key.detailKind() != TickDetailKind.NONE;
        }

        PerfWorldHotspotIndex.Key hotspotKey() {
            if (key.type() == TickType.ENTITY && key.entityId() >= 0) {
                return PerfWorldHotspotIndex.Key.entity(key.entityId());
            }
            return key.type() == TickType.BLOCK_ENTITY && aggregate.positioned
                    ? PerfWorldHotspotIndex.Key.blockEntity(aggregate.x, aggregate.y, aggregate.z)
                    : null;
        }

        PerfWorldHotspotIndex.Entry toHotspot() {
            PerfWorldHotspotIndex.Cost cost = aggregate.stats.hotspotCost(tickCount);
            return new PerfWorldHotspotIndex.Entry(hotspotKey(), key.dimension(),
                    aggregate.x, aggregate.y, aggregate.z, key.detailKind(), key.detailId(),
                    cost.samples(), cost.maxSamplesQ8());
        }

        static ObjectCandidate newer(ObjectCandidate left, ObjectCandidate right) {
            if (left.aggregate.lastSeenTick != right.aggregate.lastSeenTick) {
                return left.aggregate.lastSeenTick > right.aggregate.lastSeenTick ? left : right;
            }
            return HOTTEST_FIRST.compare(left, right) <= 0 ? left : right;
        }
    }

    private record StableObjectId(int type, String dimension, String className,
                                  String detailId, int detailKind, int entityId,
                                  long fixedPosition, int x, int y, int z,
                                  String eventLoop, String displayName) {
        static StableObjectId from(TickSamples.TickableKey key,
                                   PerfSampleAggregator.TickableAggregate aggregate) {
            return new StableObjectId(key.type().ordinal(), key.dimension(),
                    key.workClass().getName(), nullableString(key.detailId()),
                    key.detailKind().ordinal(), key.entityId(), key.fixedPosition(),
                    aggregate.x, aggregate.y, aggregate.z,
                    nullableString(key.eventLoop()), nullableString(key.displayName()));
        }

        private static String nullableString(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    private static final class ObjectSummary {
        int count;
        double representativeWeight = -1.0D;
        int x;
        int y;
        int z;

        void add(PerfSampleAggregator.TickableAggregate aggregate) {
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

    private record HotspotIdentity(String dimension, PerfWorldHotspotIndex.Key key) {
        static HotspotIdentity from(
                TickSamples.TickableKey sampled, PerfWorldHotspotIndex.Key wireKey) {
            String scopedDimension = sampled.type() == TickType.BLOCK_ENTITY
                    ? sampled.dimension() : "";
            return new HotspotIdentity(scopedDimension, wireKey);
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
}
