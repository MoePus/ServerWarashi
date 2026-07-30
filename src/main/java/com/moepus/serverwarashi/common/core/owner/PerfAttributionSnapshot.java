package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PerfAttributionSnapshot {
    private static final Owner SYSTEM_OWNER = new Owner(
            PerfReportData.AttributionKind.SYSTEM, "system", "System", false, 0L, null);
    private static final Owner UNKNOWN_OWNER = new Owner(
            PerfReportData.AttributionKind.UNKNOWN, "unknown", "Unknown", false, 0L, null);
    public static final Bucket SYSTEM = new Bucket(SYSTEM_OWNER,
            new Source("system", "System", null), 0, 0);
    public static final Bucket UNKNOWN = new Bucket(UNKNOWN_OWNER,
            new Source("unknown", "Unknown", null), 0, 0);
    private final Map<ChunkKey, List<Bucket>> byChunk;
    private final List<Bucket> buckets;
    private volatile LoaderPlan reportLoaderPlan;

    private PerfAttributionSnapshot(Map<ChunkKey, List<Bucket>> byChunk) {
        this.byChunk = Map.copyOf(byChunk);
        Map<SourceIdentity, Bucket> unique = new HashMap<>();
        byChunk.values().forEach(values -> values.forEach(
                bucket -> unique.put(SourceIdentity.from(bucket), bucket)));
        buckets = List.copyOf(unique.values());
    }

    public static PerfAttributionSnapshot capture(MinecraftServer server) {
        return captureWithFingerprint(server).snapshot();
    }

    public static Capture captureWithFingerprint(MinecraftServer server) {
        Map<ChunkKey, Set<Bucket>> expanded = new HashMap<>();
        TicketTopologyFingerprint fingerprint = new TicketTopologyFingerprint();
        for (ServerLevel level : server.getAllLevels()) {
            fingerprint.addDimension(level);
            captureLevel(level, expanded, fingerprint);
        }
        return new Capture(fromExpanded(expanded), fingerprint.value());
    }

    public static PerfAttributionSnapshot captureLoaderLayout(MinecraftServer server) {
        Map<ChunkKey, Set<Bucket>> expanded = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            captureLoaderLevel(level, expanded);
        }
        return fromExpanded(expanded);
    }

    private static PerfAttributionSnapshot fromExpanded(Map<ChunkKey, Set<Bucket>> expanded) {
        Map<ChunkKey, List<Bucket>> result = new HashMap<>();
        expanded.forEach((chunk, buckets) -> result.put(chunk, List.copyOf(buckets)));
        return new PerfAttributionSnapshot(withChunkCounts(result));
    }

    public static long ticketFingerprint(MinecraftServer server) {
        return TicketTopologyFingerprint.capture(server);
    }

    public static PerfAttributionSnapshot create(String dimension, Map<Long, List<Bucket>> chunks) {
        Map<ChunkKey, List<Bucket>> result = new HashMap<>();
        chunks.forEach((position, buckets) ->
                result.put(new ChunkKey(dimension, position), List.copyOf(buckets)));
        return new PerfAttributionSnapshot(withChunkCounts(result));
    }

    public List<Bucket> owners(String dimension, long chunkPos) {
        return byChunk.getOrDefault(new ChunkKey(dimension, chunkPos), List.of(UNKNOWN));
    }

    public List<Bucket> buckets() {
        return buckets;
    }

    public LoaderPlan loaderPlan(int sourceLimit, int regionLimit, int maxRegionSpan) {
        Map<LoaderRegionPlanner.SourceKey, Set<Long>> grouped = new HashMap<>();
        for (var entry : byChunk.entrySet()) {
            for (Bucket bucket : entry.getValue()) {
                if (isLoaderSource(bucket)) {
                    var key = new LoaderRegionPlanner.SourceKey(
                            entry.getKey().dimension, bucket.source.id,
                            bucket.source.name, bucket.source.position);
                    grouped.computeIfAbsent(key, ignored -> new HashSet<>()).add(entry.getKey().chunkPos);
                }
            }
        }
        return LoaderRegionPlanner.plan(grouped,
                new LoaderRegionPlanner.Options(sourceLimit, regionLimit, maxRegionSpan));
    }

    public LoaderPlan reportLoaderPlan() {
        LoaderPlan cached = reportLoaderPlan;
        if (cached != null) {
            return cached;
        }
        cached = loaderPlan(PerfReportLimits.MAX_LOADER_SOURCES,
                PerfReportLimits.MAX_LOADER_REGIONS, PerfReportLimits.MAX_LOADER_REGION_SPAN);
        reportLoaderPlan = cached;
        return cached;
    }

    private static boolean isLoaderSource(Bucket bucket) {
        return bucket.owner.kind == PerfReportData.AttributionKind.LOADER
                || bucket.owner.kind == PerfReportData.AttributionKind.PLAYER
                && !"self".equals(bucket.source.id);
    }

    private static void captureLevel(ServerLevel level,
                                     Map<ChunkKey, Set<Bucket>> output,
                                     TicketTopologyFingerprint fingerprint) {
        String dimension = level.dimension().location().toString();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            fingerprint.addTickets(chunkPos, tickets);
            for (Ticket<?> ticket : tickets) {
                if (!TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
                    resolve(level, chunkPos, ticket).forEach(
                            bucket -> addInfluence(output, dimension, chunkPos, bucket));
                }
            }
        });
    }

    private static void captureLoaderLevel(ServerLevel level, Map<ChunkKey, Set<Bucket>> output) {
        String dimension = level.dimension().location().toString();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
                    continue;
                }
                List<Bucket> c2meBuckets = C2meNoTickAttribution.resolve(level, chunkPos, ticket);
                if (c2meBuckets != null) {
                    c2meBuckets.forEach(bucket -> addInfluence(output, dimension, chunkPos, bucket));
                    continue;
                }
                var forced = TicketOwnerResolver.readForcedOwner(
                        TicketUtils.asPauseable(ticket).serverWarashi$getKey());
                forced.ifPresent(owner -> addInfluence(output, dimension, chunkPos,
                        forcedBucket(level, owner, ticket)));
            }
        });
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

    private static List<Bucket> resolve(ServerLevel level, long chunkPos, Ticket<?> ticket) {
        String typeName = ticket.getType().toString();
        if (ticket.getType() == TicketType.PLAYER) {
            return PlayerAttribution.selfBuckets(level, chunkPos);
        }
        List<Bucket> c2meBuckets = C2meNoTickAttribution.resolve(level, chunkPos, ticket);
        if (c2meBuckets != null) {
            return c2meBuckets;
        }
        var forced = TicketOwnerResolver.readForcedOwner(
                TicketUtils.asPauseable(ticket).serverWarashi$getKey());
        if (forced.isPresent()) {
            return List.of(forcedBucket(level, forced.get(), ticket));
        }
        if (isVanillaSystem(ticket.getType())) {
            return List.of(systemBucket(level, ticket, typeName));
        }
        return List.of(unknownBucket(level, ticket, typeName));
    }

    private static Bucket forcedBucket(ServerLevel level,
                                       TicketOwnerResolver.ForcedOwner forced,
                                       Ticket<?> ticket) {
        Bucket ftbBucket = FtbForcedChunkAttribution.resolve(level, forced, ticket).orElse(null);
        if (ftbBucket != null) {
            return ftbBucket;
        }
        Loader loader = loader(level, forced);
        Owner resolved = forced.owner() instanceof UUID uuid
                ? PlayerAttribution.forEntity(level, uuid)
                : UNKNOWN_OWNER;
        String sourceId = level.dimension().location() + ":" + forced.id() + ":" + forced.owner();
        Owner owner = resolved.kind() == PerfReportData.AttributionKind.PLAYER
                ? resolved : loaderOwner(sourceId, loader);
        return new Bucket(owner, new Source(sourceId, loader.name, loader.position),
                0, 0, LoaderTicketInfluence.blockTickingRadius(ticket));
    }

    private static Loader loader(ServerLevel level, TicketOwnerResolver.ForcedOwner forced) {
        if (forced.owner() instanceof BlockPos pos) {
            return new Loader(loaderBlockId(level, pos, String.valueOf(forced.id())), position(level, pos));
        }
        if (forced.owner() instanceof UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                String name = entity.hasCustomName() ? entity.getName().getString() : entity.getClass().getSimpleName();
                return new Loader(name, position(level, entity.blockPosition()));
            }
        }
        return new Loader(String.valueOf(forced.id()), null);
    }

    private static String loaderBlockId(ServerLevel level, BlockPos pos, String fallback) {
        var holder = level.getChunkSource().chunkMap.visibleChunkMap.get(ChunkPos.asLong(pos));
        if (holder == null) {
            return fallback;
        }
        var chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
        return chunk == null ? fallback
                : BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
    }

    private static Owner loaderOwner(String id, Loader loader) {
        return new Owner(PerfReportData.AttributionKind.LOADER, id, loader.name,
                false, 0L, loader.position);
    }

    private static Bucket systemBucket(ServerLevel level, Ticket<?> ticket, String typeName) {
        Position position = ticket.getType() == TicketType.START
                ? position(level, level.getSharedSpawnPos()) : null;
        return new Bucket(SYSTEM_OWNER, source(level, ticket, typeName, position), 0, 0);
    }

    private static Bucket unknownBucket(ServerLevel level, Ticket<?> ticket, String typeName) {
        Object key = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        Position position = key instanceof BlockPos pos ? position(level, pos)
                : key instanceof ChunkPos pos ? position(level, pos.getWorldPosition()) : null;
        return new Bucket(UNKNOWN_OWNER, source(level, ticket, typeName, position), 0, 0);
    }

    private static Source source(ServerLevel level, Ticket<?> ticket, String name, Position position) {
        Object key = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        String id = level.dimension().location() + ":" + name + ":" + key;
        return new Source(id, name, position);
    }

    private static Position position(ServerLevel level, BlockPos pos) {
        return new Position(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
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

    private static boolean isVanillaSystem(TicketType<?> type) {
        return type == TicketType.START || type == TicketType.DRAGON || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT || type == TicketType.UNKNOWN;
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
                               List<LoaderRegion> regions) {
        public LoaderSource {
            regions = List.copyOf(regions);
        }
    }

    public record LoaderRegion(int minChunkX, int minChunkZ, int width, int height) {
    }

    public record Owner(PerfReportData.AttributionKind kind, String id, String name,
                        boolean online, long lastLoginEpochMillis, Position position) {
    }

    public record Source(String id, String name, Position position) {
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
    }

    private record ChunkKey(String dimension, long chunkPos) {
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

    private record Loader(String name, Position position) {
    }

}
