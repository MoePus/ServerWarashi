package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

final class PerfOwnerAggregator {
    private static final int VIEW_LIMIT = 1024;
    private static final int OWNER_LIMIT_PER_METRIC = PerfReportLimits.MAX_ENTRIES / 2;
    private static final int TRACKED_SOURCE_LIMIT = 32_768;
    private static final int DROPPED_HASH_LIMIT = 65_536;
    private static final int REPLACEMENT_INTERVAL = 1_024;
    private final Map<OwnerKey, SampleStatistics> owners = new HashMap<>();
    private final Map<OwnerKey, PerfAttributionSnapshot.Bucket> ownerMetadata = new HashMap<>();
    private final Map<OwnerSourceKey, SourceAggregate> sources = new HashMap<>();
    private final IntOpenHashSet droppedSourceHashes = new IntOpenHashSet();
    private PerfAttributionSnapshot latestAttribution;
    private int dropsUntilReplacement = REPLACEMENT_INTERVAL;

    void accept(TickSamples tick, SplittableRandom random) {
        updateMetadata(tick.attribution);
        TickAttribution attribution = new TickAttribution(random);
        for (var entry : tick.chunks.entrySet()) {
            double nanos = estimate(tick.durationNanos, entry.getValue(), tick.totalSamples);
            List<PerfAttributionSnapshot.Bucket> buckets = tick.attribution == null
                    ? List.of(PerfAttributionSnapshot.UNKNOWN)
                    : tick.attribution.owners(entry.getKey().dimension(), entry.getKey().position());
            attribution.record(buckets, nanos, entry.getValue());
        }
        attribution.commit();
    }

    void updateMetadata(PerfAttributionSnapshot attribution) {
        if (attribution == null || attribution == latestAttribution) {
            return;
        }
        latestAttribution = attribution;
        attribution.buckets().forEach(bucket -> {
            OwnerKey owner = OwnerKey.from(bucket);
            owners.computeIfAbsent(owner, ignored -> new SampleStatistics());
            ownerMetadata.put(owner, bucket);
            SourceAggregate source = sourceAggregate(OwnerSourceKey.from(bucket));
            if (source != null) {
                source.metadata = bucket;
            }
        });
    }

    Build build(long tickCount) {
        List<SourceChild> children = sourceEntries(tickCount);
        var limited = PerfReportLimits.topMetricUnion(children, VIEW_LIMIT, value -> value.entry.metric());
        Map<OwnerKey, List<PerfReportData.OwnerSourceEntry>> grouped = new HashMap<>();
        limited.entries().forEach(child -> grouped
                .computeIfAbsent(child.owner, ignored -> new ArrayList<>()).add(child.entry));
        List<PerfReportData.OwnerEntry> entries = new ArrayList<>(owners.size());
        owners.forEach((owner, stats) -> entries.add(ownerEntry(owner, stats, grouped, tickCount)));
        var limitedOwners = PerfReportLimits.topMetricUnion(entries, OWNER_LIMIT_PER_METRIC,
                PerfReportData.OwnerEntry::metric);
        LoaderBuild loaders = loaderSources();
        return new Build(limitedOwners.entries(), omitted((long) limited.omitted() + limitedOwners.omitted()),
                loaders.omittedSources, loaders.sources);
    }

    private LoaderBuild loaderSources() {
        if (latestAttribution == null) {
            return new LoaderBuild(0, List.of());
        }
        var plan = latestAttribution.loaderPlan(PerfReportLimits.MAX_LOADER_SOURCES,
                PerfReportLimits.MAX_LOADER_REGIONS, PerfReportLimits.MAX_LOADER_REGION_SPAN);
        List<PerfReportData.LoaderSourceEntry> sources = plan.sources().stream()
                .map(PerfOwnerAggregator::loaderSource)
                .toList();
        return new LoaderBuild(plan.omittedSources(), sources);
    }

    private static PerfReportData.LoaderSourceEntry loaderSource(
            PerfAttributionSnapshot.LoaderSource source) {
        List<PerfReportData.LoaderRegion> regions = source.regions().stream()
                .map(region -> new PerfReportData.LoaderRegion(region.minChunkX(), region.minChunkZ(),
                        region.width(), region.height()))
                .toList();
        return new PerfReportData.LoaderSourceEntry(source.dimension(), source.sourceId(),
                source.loaderName(), loaderPosition(source.position()), source.omittedRegions(), regions);
    }

    private static PerfReportData.LoaderPosition loaderPosition(PerfAttributionSnapshot.Position position) {
        return position == null ? null
                : new PerfReportData.LoaderPosition(position.x(), position.y(), position.z());
    }

    private SourceAggregate sourceAggregate(OwnerSourceKey key) {
        SourceAggregate existing = sources.get(key);
        if (existing != null || sources.size() < TRACKED_SOURCE_LIMIT) {
            return existing != null ? existing : addSource(key);
        }
        recordDropped(key);
        if (--dropsUntilReplacement > 0) {
            return null;
        }
        dropsUntilReplacement = REPLACEMENT_INTERVAL;
        evictLightest();
        return addSource(key);
    }

    private SourceAggregate addSource(OwnerSourceKey key) {
        SourceAggregate source = new SourceAggregate();
        sources.put(key, source);
        return source;
    }

    private void evictLightest() {
        OwnerSourceKey lightest = null;
        double weight = Double.POSITIVE_INFINITY;
        for (var entry : sources.entrySet()) {
            double candidate = entry.getValue().stats.totalNanos();
            if (candidate < weight) {
                lightest = entry.getKey();
                weight = candidate;
            }
        }
        if (lightest != null) {
            recordDropped(lightest);
            sources.remove(lightest);
        }
    }

    private void recordDropped(Object key) {
        if (droppedSourceHashes.size() < DROPPED_HASH_LIMIT) {
            droppedSourceHashes.add(key.hashCode());
        }
    }

    private List<SourceChild> sourceEntries(long tickCount) {
        List<SourceChild> result = new ArrayList<>(sources.size());
        sources.forEach((key, source) -> {
            if (source.metadata != null) {
                var metadata = source.metadata;
                var detail = metadata.source();
                result.add(new SourceChild(key.owner, new PerfReportData.OwnerSourceEntry(
                        detail.id(), detail.name(), position(detail.position()), metadata.sourceChunkCount(),
                        source.stats.metric(tickCount))));
            }
        });
        return result;
    }

    private PerfReportData.OwnerEntry ownerEntry(
            OwnerKey owner,
            SampleStatistics stats,
            Map<OwnerKey, List<PerfReportData.OwnerSourceEntry>> grouped,
            long tickCount) {
        PerfAttributionSnapshot.Bucket metadata = ownerMetadata.get(owner);
        PerfAttributionSnapshot.Owner detail = metadata.owner();
        return new PerfReportData.OwnerEntry(owner.kind, owner.id, detail.name(), detail.online(),
                detail.lastLoginEpochMillis(), position(detail.position()), metadata.ownerChunkCount(),
                stats.metric(tickCount), grouped.getOrDefault(owner, List.of()));
    }

    private static PerfReportData.Position position(PerfAttributionSnapshot.Position position) {
        return position == null ? null : new PerfReportData.Position(
                position.dimension(), position.x(), position.y(), position.z());
    }

    private static double estimate(long durationNanos, int samples, int totalSamples) {
        return durationNanos * (samples / (double) totalSamples);
    }

    static double equalShare(double value, int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive");
        }
        return value / bucketCount;
    }

    private int omitted(long limited) {
        return (int) Math.min(Integer.MAX_VALUE, limited + droppedSourceHashes.size());
    }

    record Build(List<PerfReportData.OwnerEntry> entries,
                 int omitted,
                 int omittedLoaderSources,
                 List<PerfReportData.LoaderSourceEntry> loaderSources) {
    }

    private record LoaderBuild(int omittedSources, List<PerfReportData.LoaderSourceEntry> sources) {
    }

    private record OwnerKey(PerfReportData.AttributionKind kind, String id) {
        static OwnerKey from(PerfAttributionSnapshot.Bucket bucket) {
            return new OwnerKey(bucket.owner().kind(), bucket.owner().id());
        }
    }

    private record OwnerSourceKey(OwnerKey owner, String sourceId) {
        static OwnerSourceKey from(PerfAttributionSnapshot.Bucket bucket) {
            return new OwnerSourceKey(OwnerKey.from(bucket), bucket.source().id());
        }
    }

    private record SourceChild(OwnerKey owner, PerfReportData.OwnerSourceEntry entry) {
    }

    private static final class SourceAggregate {
        final SampleStatistics stats = new SampleStatistics();
        PerfAttributionSnapshot.Bucket metadata;
    }

    private final class TickAttribution {
        private final Map<OwnerKey, Estimate> ownerCosts = new HashMap<>();
        private final Map<SourceAggregate, Estimate> sourceCosts = new HashMap<>();
        private final SplittableRandom random;

        private TickAttribution(SplittableRandom random) {
            this.random = random;
        }

        void record(List<PerfAttributionSnapshot.Bucket> buckets, double nanos, int samples) {
            double sharedNanos = equalShare(nanos, buckets.size());
            double sharedSamples = equalShare(samples, buckets.size());
            for (PerfAttributionSnapshot.Bucket bucket : buckets) {
                OwnerKey owner = OwnerKey.from(bucket);
                ownerMetadata.put(owner, bucket);
                ownerCosts.computeIfAbsent(owner, ignored -> new Estimate()).add(sharedNanos, sharedSamples);
                SourceAggregate source = sourceAggregate(OwnerSourceKey.from(bucket));
                if (source != null) {
                    source.metadata = bucket;
                    sourceCosts.computeIfAbsent(source, ignored -> new Estimate()).add(sharedNanos, sharedSamples);
                }
            }
        }

        void commit() {
            ownerCosts.forEach((owner, estimate) -> owners
                    .computeIfAbsent(owner, ignored -> new SampleStatistics())
                    .record(estimate.nanos, estimate.samples, random));
            sourceCosts.forEach((source, estimate) ->
                    source.stats.record(estimate.nanos, estimate.samples, random));
        }
    }

    private static final class Estimate {
        double nanos;
        double samples;

        void add(double nanos, double samples) {
            this.nanos += nanos;
            this.samples += samples;
        }
    }
}
