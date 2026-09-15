package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderStateData;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PerfAttributionSnapshot {
    private static final Owner UNKNOWN_OWNER = new Owner(
            PerfReportData.AttributionKind.UNKNOWN, "unknown", "Unknown", false, 0L, null);
    public static final Bucket UNKNOWN = new Bucket(UNKNOWN_OWNER,
            new Source("unknown", "Unknown", null), 0, 0);
    private final Map<ChunkKey, List<Bucket>> byChunk;
    private final Map<ChunkKey, List<Bucket>> loaderByChunk;
    private final Map<BlockLoaderRef, List<LoaderTicketTarget>> loaderTargets;
    private final Map<BlockLoaderRef, LoaderControl> loaderControls;
    private final List<Bucket> buckets;
    private volatile LoaderPlan reportLoaderPlan;
    private volatile long loaderControlRevision;

    private PerfAttributionSnapshot(Map<ChunkKey, List<Bucket>> byChunk,
                                    Map<ChunkKey, List<Bucket>> loaderByChunk,
                                    Map<BlockLoaderRef, List<LoaderTicketTarget>> loaderTargets,
                                    Map<BlockLoaderRef, LoaderControl> loaderControls) {
        this.byChunk = Collections.unmodifiableMap(byChunk);
        this.loaderByChunk = Collections.unmodifiableMap(loaderByChunk);
        this.loaderTargets = copyTargets(loaderTargets);
        this.loaderControls = new ConcurrentHashMap<>(loaderControls);
        Map<SourceIdentity, Bucket> unique = new HashMap<>();
        byChunk.values().forEach(values -> values.forEach(
                bucket -> unique.put(SourceIdentity.from(bucket), bucket)));
        buckets = List.copyOf(unique.values());
    }

    public static PerfAttributionSnapshot capture(MinecraftServer server) {
        CaptureMaps maps = new CaptureMaps();
        for (ServerLevel level : server.getAllLevels()) {
            captureLevel(level, maps, null, true);
        }
        return fromCapture(maps);
    }

    public static Capture captureWithFingerprint(MinecraftServer server) {
        CaptureMaps maps = new CaptureMaps();
        TicketTopologyFingerprint fingerprint = new TicketTopologyFingerprint();
        for (ServerLevel level : server.getAllLevels()) {
            fingerprint.addDimension(level);
            captureLevel(level, maps, fingerprint, true);
        }
        return new Capture(fromCapture(maps), fingerprint.value());
    }

    public static PerfAttributionSnapshot captureLoaderLayout(MinecraftServer server) {
        CaptureMaps maps = new CaptureMaps();
        for (ServerLevel level : server.getAllLevels()) {
            captureLevel(level, maps, null, false);
        }
        return fromCapture(maps);
    }

    private static PerfAttributionSnapshot fromCapture(CaptureMaps maps) {
        return new PerfAttributionSnapshot(withChunkCounts(freezeExpanded(maps.active)),
                withChunkCounts(freezeExpanded(maps.loaders)), maps.targets, maps.controls);
    }

    private static Map<ChunkKey, List<Bucket>> freezeExpanded(Map<ChunkKey, Set<Bucket>> expanded) {
        Map<ChunkKey, List<Bucket>> result = new HashMap<>();
        expanded.forEach((chunk, buckets) -> result.put(chunk, List.copyOf(buckets)));
        return result;
    }

    private static Map<BlockLoaderRef, List<LoaderTicketTarget>> copyTargets(
            Map<BlockLoaderRef, List<LoaderTicketTarget>> source) {
        Map<BlockLoaderRef, List<LoaderTicketTarget>> result = new HashMap<>();
        source.forEach((ref, targets) -> result.put(ref, new java.util.ArrayList<>(targets)));
        return result;
    }

    public static long ticketFingerprint(MinecraftServer server) {
        return TicketTopologyFingerprint.capture(server);
    }

    public static PerfAttributionSnapshot create(String dimension, Map<Long, List<Bucket>> chunks) {
        return create(dimension, chunks, Map.of());
    }

    public static PerfAttributionSnapshot create(
            String dimension,
            Map<Long, List<Bucket>> chunks,
            Map<BlockLoaderRef, LoaderControl> controls) {
        Map<ChunkKey, List<Bucket>> result = new HashMap<>();
        chunks.forEach((position, buckets) ->
                result.put(new ChunkKey(dimension, position), List.copyOf(buckets)));
        Map<ChunkKey, List<Bucket>> counted = withChunkCounts(result);
        return new PerfAttributionSnapshot(counted, counted, Map.of(), controls);
    }

    public List<Bucket> owners(String dimension, long chunkPos) {
        return byChunk.getOrDefault(new ChunkKey(dimension, chunkPos), List.of(UNKNOWN));
    }

    public List<Bucket> buckets() {
        return buckets;
    }

    public LoaderPlan loaderPlan(int sourceLimit, int regionLimit, int maxRegionSpan) {
        Map<LoaderRegionPlanner.SourceKey, Set<Long>> grouped = new HashMap<>();
        for (var entry : loaderByChunk.entrySet()) {
            for (Bucket bucket : entry.getValue()) {
                if (isLoaderSource(bucket)) {
                    var key = new LoaderRegionPlanner.SourceKey(
                            entry.getKey().dimension(), bucket.source.id,
                            bucket.source.name, bucket.source.position, bucket.source.loaderRef);
                    grouped.computeIfAbsent(key, ignored -> new LongOpenHashSet())
                            .add(entry.getKey().chunkPos());
                }
            }
        }
        LoaderPlan geometry = LoaderRegionPlanner.plan(grouped,
                new LoaderRegionPlanner.Options(sourceLimit, regionLimit, maxRegionSpan));
        return withLoaderControls(geometry);
    }

    public LoaderPlan reportLoaderPlan() {
        LoaderPlan cached = reportLoaderPlan;
        if (cached != null) {
            return withLoaderControls(cached);
        }
        cached = withoutLoaderControls(loaderPlan(PerfReportLimits.MAX_LOADER_SOURCES,
                PerfReportLimits.MAX_LOADER_REGIONS, PerfReportLimits.MAX_LOADER_REGION_SPAN));
        reportLoaderPlan = cached;
        return withLoaderControls(cached);
    }

    public synchronized List<LoaderTicketTarget> loaderTargets(BlockLoaderRef ref) {
        return List.copyOf(loaderTargets.getOrDefault(ref, List.of()));
    }

    public Optional<LoaderControl> loaderControl(BlockLoaderRef ref) {
        return Optional.ofNullable(loaderControls.get(ref));
    }

    public long loaderControlRevision() {
        return loaderControlRevision;
    }

    public synchronized boolean appendLoaderTarget(BlockLoaderRef ref, LoaderTicketTarget target) {
        List<LoaderTicketTarget> targets = loaderTargets.get(ref);
        if (targets == null || targets.stream().anyMatch(existing -> existing.ticket() == target.ticket())) {
            return false;
        }
        targets.add(target);
        LoaderControl control = loaderControls.get(ref);
        if (control != null && !TicketUtils.asPauseable(target.ticket()).serverWarashi$isPaused()) {
            LoaderControl updated = new LoaderControl(ref, control.manuallyFrozen, true);
            if (!control.equals(updated)) {
                loaderControls.put(ref, updated);
                loaderControlRevision++;
            }
        }
        return true;
    }

    public synchronized boolean updateLoaderControl(BlockLoaderRef ref,
                                                     boolean manuallyFrozen,
                                                     boolean working) {
        LoaderControl previous = loaderControls.get(ref);
        if (previous == null) {
            return false;
        }
        LoaderControl updated = new LoaderControl(ref, manuallyFrozen, working);
        if (previous.equals(updated)) {
            return false;
        }
        loaderControls.put(ref, updated);
        loaderControlRevision++;
        return true;
    }

    private LoaderPlan withLoaderControls(LoaderPlan plan) {
        return new LoaderPlan(plan.sources.stream().map(source -> {
            LoaderControl control = source.omittedRegions == 0 && source.loaderRef != null
                    ? loaderControls.get(source.loaderRef) : null;
            return source.withControl(control);
        }).toList(), plan.omittedSources);
    }

    private static LoaderPlan withoutLoaderControls(LoaderPlan plan) {
        return new LoaderPlan(plan.sources.stream().map(source -> source.withControl(null)).toList(),
                plan.omittedSources);
    }

    private static boolean isLoaderSource(Bucket bucket) {
        if (C2meNoTickAttribution.isViewDistanceSource(bucket.source)) {
            return false;
        }
        return bucket.owner.kind == PerfReportData.AttributionKind.LOADER
                || bucket.owner.kind == PerfReportData.AttributionKind.PLAYER
                && !"self".equals(bucket.source.id);
    }

    private static void captureLevel(ServerLevel level,
                                     CaptureMaps maps,
                                     TicketTopologyFingerprint fingerprint,
                                     boolean collectAttribution) {
        String dimension = level.dimension().location().toString();
        C2meNoTickAttribution.CaptureContext c2me = collectAttribution
                ? C2meNoTickAttribution.captureContext(level,
                    new C2meNoTickAttribution.CaptureTarget(dimension, maps.active)) : null;
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            if (fingerprint != null) {
                fingerprint.addTickets(chunkPos, tickets);
            }
            for (Ticket<?> ticket : tickets) {
                if (!C2meNoTickAttribution.capture(c2me, chunkPos, ticket)) {
                    captureTicket(level, dimension, chunkPos, ticket, maps, collectAttribution);
                }
            }
        });
    }

    private static void captureTicket(ServerLevel level,
                                      String dimension,
                                      long chunkPos,
                                      Ticket<?> ticket,
                                      CaptureMaps maps,
                                      boolean collectAttribution) {
        PerfTicketAttribution.Resolved resolved = PerfTicketAttribution.resolve(
                level, chunkPos, ticket, collectAttribution);
        boolean paused = TicketUtils.asPauseable(ticket).serverWarashi$isPaused();
        resolved.buckets().stream().filter(PerfAttributionSnapshot::isLoaderSource)
                .forEach(bucket -> addInfluence(maps.loaders, dimension, chunkPos, bucket));
        if (collectAttribution && !paused) {
            resolved.buckets().forEach(bucket -> addInfluence(maps.active, dimension, chunkPos, bucket));
        }
        if (resolved.loaderRef() != null) {
            maps.addTarget(level, resolved.loaderRef(), new LoaderTicketTarget(chunkPos, ticket), !paused);
        }
    }

    private static void addInfluence(Map<ChunkKey, Set<Bucket>> output,
                                     String dimension,
                                     long center,
                                     Bucket bucket) {
        Bucket stored = bucket.withoutInfluence();
        LoaderTicketInfluence.visit(center, bucket.influenceRadius, position -> output
                .computeIfAbsent(new ChunkKey(dimension, position), ignored -> new LinkedHashSet<>())
                .add(stored));
    }

    private static Map<ChunkKey, List<Bucket>> withChunkCounts(Map<ChunkKey, List<Bucket>> source) {
        Map<OwnerIdentity, Integer> ownerCounts = new HashMap<>();
        Map<SourceIdentity, Integer> sourceCounts = new HashMap<>();
        source.values().forEach(buckets -> countChunk(buckets, ownerCounts, sourceCounts));
        Map<ChunkKey, List<Bucket>> result = new HashMap<>();
        source.forEach((chunk, buckets) -> result.put(chunk, buckets.stream()
                .map(bucket -> bucket.withCounts(ownerCounts.get(OwnerIdentity.from(bucket)),
                        sourceCounts.get(SourceIdentity.from(bucket))))
                .toList()));
        return result;
    }

    private static void countChunk(List<Bucket> buckets,
                                   Map<OwnerIdentity, Integer> ownerCounts,
                                   Map<SourceIdentity, Integer> sourceCounts) {
        Set<OwnerIdentity> owners = new HashSet<>();
        Set<SourceIdentity> sources = new HashSet<>();
        buckets.forEach(bucket -> {
            owners.add(OwnerIdentity.from(bucket));
            sources.add(SourceIdentity.from(bucket));
        });
        owners.forEach(owner -> ownerCounts.merge(owner, 1, Integer::sum));
        sources.forEach(source -> sourceCounts.merge(source, 1, Integer::sum));
    }

    public record Capture(PerfAttributionSnapshot snapshot, long ticketFingerprint) {
    }

    public record Position(String dimension, int x, int y, int z) {
    }

    public record LoaderPlan(List<LoaderSource> sources, int omittedSources) {
        public LoaderPlan {
            sources = List.copyOf(sources);
        }
    }

    public record LoaderSource(String dimension,
                               String sourceId,
                               String loaderName,
                               Position position,
                               int omittedRegions,
                               List<LoaderRegion> regions,
                               BlockLoaderRef loaderRef,
                               LoaderControl control) {
        public LoaderSource {
            regions = List.copyOf(regions);
        }

        LoaderSource withControl(LoaderControl nextControl) {
            return new LoaderSource(dimension, sourceId, loaderName, position,
                    omittedRegions, regions, loaderRef, nextControl);
        }
    }

    public record LoaderControl(BlockLoaderRef ref, boolean manuallyFrozen, boolean working) {
    }

    public record LoaderRegion(int minChunkX, int minChunkZ, int width, int height) {
    }

    public record Owner(PerfReportData.AttributionKind kind, String id, String name,
                        boolean online, long lastLoginEpochMillis, Position position) {
    }

    public record Source(String id, String name, Position position, BlockLoaderRef loaderRef) {
        public Source(String id, String name, Position position) {
            this(id, name, position, null);
        }
    }

    public record Bucket(Owner owner,
                         Source source,
                         int ownerChunkCount,
                         int sourceChunkCount,
                         int influenceRadius) {
        public Bucket(Owner owner, Source source, int ownerChunkCount, int sourceChunkCount) {
            this(owner, source, ownerChunkCount, sourceChunkCount, 0);
        }

        Bucket withCounts(int ownerChunks, int sourceChunks) {
            return new Bucket(owner, source, ownerChunks, sourceChunks, influenceRadius);
        }

        Bucket withoutInfluence() {
            return influenceRadius == 0 ? this
                    : new Bucket(owner, source, ownerChunkCount, sourceChunkCount, 0);
        }

        Bucket withLoaderRef(BlockLoaderRef ref) {
            return ref == null ? this : new Bucket(owner,
                    new Source(source.id, source.name, source.position, ref),
                    ownerChunkCount, sourceChunkCount, influenceRadius);
        }
    }

    private static final class CaptureMaps {
        final Map<ChunkKey, Set<Bucket>> active = new HashMap<>();
        final Map<ChunkKey, Set<Bucket>> loaders = new HashMap<>();
        final Map<BlockLoaderRef, List<LoaderTicketTarget>> targets = new HashMap<>();
        final Map<BlockLoaderRef, LoaderControl> controls = new HashMap<>();

        void addTarget(ServerLevel level, BlockLoaderRef ref,
                       LoaderTicketTarget target, boolean ticketWorking) {
            targets.computeIfAbsent(ref, ignored -> new java.util.ArrayList<>()).add(target);
            LoaderControl previous = controls.get(ref);
            boolean frozen = previous == null
                    ? LoaderStateData.get(level).isManuallyFrozen(ref.controllerId(), ref.ownerPos())
                    : previous.manuallyFrozen;
            controls.put(ref, new LoaderControl(ref, frozen,
                    ticketWorking || previous != null && previous.working));
        }
    }

    private record OwnerIdentity(PerfReportData.AttributionKind kind, String id) {
        static OwnerIdentity from(Bucket bucket) {
            return new OwnerIdentity(bucket.owner.kind, bucket.owner.id);
        }
    }

    private record SourceIdentity(OwnerIdentity owner, String sourceId) {
        static SourceIdentity from(Bucket bucket) {
            return new SourceIdentity(OwnerIdentity.from(bucket), bucket.source.id);
        }
    }

}
