package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PlayerAttribution {
    private PlayerAttribution() {
    }

    static List<PerfAttributionSnapshot.Bucket> selfBuckets(ServerLevel level, long chunkPos) {
        ChunkPos pos = new ChunkPos(chunkPos);
        List<PerfAttributionSnapshot.Bucket> buckets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.getChunkTrackingView().contains(pos.x, pos.z)) {
                var source = new PerfAttributionSnapshot.Source("self", "Self", null);
                buckets.add(new PerfAttributionSnapshot.Bucket(online(player), source, 0, 0));
            }
        }
        return buckets.isEmpty() ? List.of(PerfAttributionSnapshot.UNKNOWN) : buckets;
    }

    static PerfAttributionSnapshot.Owner forEntity(ServerLevel level, UUID entityId) {
        Entity entity = level.getEntity(entityId);
        if (entity instanceof ServerPlayer player) {
            return online(player);
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return known(level.getServer(), ownable.getOwnerUUID());
        }
        if (entity == null && level.getServer().getProfileCache().get(entityId).isPresent()) {
            return known(level.getServer(), entityId);
        }
        return PerfAttributionSnapshot.UNKNOWN.owner();
    }

    static PerfAttributionSnapshot.Owner known(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online(online);
        }
        String name = server.getProfileCache().get(playerId)
                .map(profile -> profile.getName()).orElse(playerId.toString());
        return new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.PLAYER,
                playerId.toString(), name, false, PlayerLoginTime.fromCache(server, playerId), null);
    }

    static PerfAttributionSnapshot.Owner online(ServerPlayer player) {
        return new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.PLAYER,
                player.getUUID().toString(), player.getName().getString(), true, 0L,
                position(player.serverLevel(), player.blockPosition()));
    }

    static PerfAttributionSnapshot.Position position(ServerLevel level, BlockPos pos) {
        return new PerfAttributionSnapshot.Position(
                level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }
}
