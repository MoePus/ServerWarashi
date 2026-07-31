package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderRecord;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderStateData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;
import java.util.UUID;

final class PerfTicketAttribution {
    private static final PerfAttributionSnapshot.Owner SYSTEM_OWNER = new PerfAttributionSnapshot.Owner(
            PerfReportData.AttributionKind.SYSTEM, "system", "System", false, 0L, null);
    private static final PerfAttributionSnapshot.Owner UNKNOWN_OWNER = new PerfAttributionSnapshot.Owner(
            PerfReportData.AttributionKind.UNKNOWN, "unknown", "Unknown", false, 0L, null);

    private PerfTicketAttribution() {
    }

    static Resolved resolve(ServerLevel level, long chunkPos, Ticket<?> ticket, boolean collectAttribution) {
        List<PerfAttributionSnapshot.Bucket> c2me = C2meNoTickAttribution.resolve(level, chunkPos, ticket);
        if (c2me != null) {
            return new Resolved(c2me, null);
        }
        var forced = TicketOwnerResolver.readForcedOwner(
                TicketUtils.asPauseable(ticket).serverWarashi$getKey());
        if (forced.isPresent()) {
            BlockLoaderRef ref = loaderRef(level, forced.get());
            return new Resolved(List.of(forcedBucket(level, forced.get(), ticket, ref)), ref);
        }
        if (!collectAttribution) {
            return Resolved.EMPTY;
        }
        return new Resolved(resolveOrdinary(level, chunkPos, ticket), null);
    }

    private static List<PerfAttributionSnapshot.Bucket> resolveOrdinary(
            ServerLevel level, long chunkPos, Ticket<?> ticket) {
        if (ticket.getType() == TicketType.PLAYER) {
            return PlayerAttribution.selfBuckets(level, chunkPos);
        }
        String typeName = ticket.getType().toString();
        return List.of(isVanillaSystem(ticket.getType())
                ? systemBucket(level, ticket, typeName)
                : unknownBucket(level, ticket, typeName));
    }

    private static PerfAttributionSnapshot.Bucket forcedBucket(
            ServerLevel level,
            TicketOwnerResolver.ForcedOwner forced,
            Ticket<?> ticket,
            BlockLoaderRef ref) {
        PerfAttributionSnapshot.Bucket ftb = FtbForcedChunkAttribution.resolve(level, forced, ticket).orElse(null);
        if (ftb != null) {
            return ftb.withLoaderRef(ref);
        }
        Loader loader = loader(level, forced);
        PerfAttributionSnapshot.Owner resolved = attributedOwner(level, forced, ref);
        String sourceId = level.dimension().location() + ":" + forced.id() + ":" + forced.owner();
        PerfAttributionSnapshot.Owner owner = resolved.kind() == PerfReportData.AttributionKind.PLAYER
                ? resolved : loaderOwner(sourceId, loader);
        return new PerfAttributionSnapshot.Bucket(owner,
                new PerfAttributionSnapshot.Source(sourceId, loader.name, loader.position, ref),
                0, 0, LoaderTicketInfluence.blockTickingRadius(ticket));
    }

    private static PerfAttributionSnapshot.Owner attributedOwner(
            ServerLevel level, TicketOwnerResolver.ForcedOwner forced, BlockLoaderRef ref) {
        LoaderRecord record = ref == null ? null : LoaderStateData.get(level)
                .find(ref.controllerId(), ref.ownerPos()).orElse(null);
        if (record != null && record.placedBy().isPresent()) {
            return PlayerAttribution.known(level.getServer(), record.placedBy().get());
        }
        return forced.owner() instanceof UUID uuid
                ? PlayerAttribution.forEntity(level, uuid) : UNKNOWN_OWNER;
    }

    private static BlockLoaderRef loaderRef(ServerLevel level, TicketOwnerResolver.ForcedOwner forced) {
        return forced.id() instanceof ResourceLocation id && forced.owner() instanceof BlockPos pos
                ? BlockLoaderRef.of(level, id, pos) : null;
    }

    private static Loader loader(ServerLevel level, TicketOwnerResolver.ForcedOwner forced) {
        if (forced.owner() instanceof BlockPos pos) {
            return new Loader(loaderBlockId(level, pos, String.valueOf(forced.id())), position(level, pos));
        }
        if (forced.owner() instanceof UUID uuid) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                String name = entity.hasCustomName()
                        ? entity.getName().getString() : entity.getClass().getSimpleName();
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

    private static PerfAttributionSnapshot.Owner loaderOwner(String id, Loader loader) {
        return new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.LOADER,
                id, loader.name, false, 0L, loader.position);
    }

    private static PerfAttributionSnapshot.Bucket systemBucket(
            ServerLevel level, Ticket<?> ticket, String typeName) {
        PerfAttributionSnapshot.Position position = ticket.getType() == TicketType.START
                ? position(level, level.getSharedSpawnPos()) : null;
        return new PerfAttributionSnapshot.Bucket(SYSTEM_OWNER,
                source(level, ticket, typeName, position), 0, 0);
    }

    private static PerfAttributionSnapshot.Bucket unknownBucket(
            ServerLevel level, Ticket<?> ticket, String typeName) {
        Object key = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        PerfAttributionSnapshot.Position position = key instanceof BlockPos pos ? position(level, pos)
                : key instanceof ChunkPos pos ? position(level, pos.getWorldPosition()) : null;
        return new PerfAttributionSnapshot.Bucket(UNKNOWN_OWNER,
                source(level, ticket, typeName, position), 0, 0);
    }

    private static PerfAttributionSnapshot.Source source(
            ServerLevel level, Ticket<?> ticket, String name, PerfAttributionSnapshot.Position position) {
        Object key = TicketUtils.asPauseable(ticket).serverWarashi$getKey();
        return new PerfAttributionSnapshot.Source(
                level.dimension().location() + ":" + name + ":" + key, name, position);
    }

    private static PerfAttributionSnapshot.Position position(ServerLevel level, BlockPos pos) {
        return new PerfAttributionSnapshot.Position(
                level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean isVanillaSystem(TicketType<?> type) {
        return type == TicketType.START || type == TicketType.DRAGON || type == TicketType.PORTAL
                || type == TicketType.POST_TELEPORT || type == TicketType.UNKNOWN;
    }

    record Resolved(List<PerfAttributionSnapshot.Bucket> buckets, BlockLoaderRef loaderRef) {
        static final Resolved EMPTY = new Resolved(List.of(), null);
    }

    private record Loader(String name, PerfAttributionSnapshot.Position position) {
    }
}
