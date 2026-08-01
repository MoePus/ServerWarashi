package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.mixin.accessor.ChunkMapAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

final class C2meNoTickAttribution {
    static final String TICKET_TYPE = "c2me_notickvd";
    private static final String SOURCE_ID_MARKER = ":" + TICKET_TYPE + ":";
    private static final int VIEW_DISTANCE_PADDING = 1;

    private C2meNoTickAttribution() {
    }

    static List<PerfAttributionSnapshot.Bucket> resolve(
            ServerLevel level, long chunkPos, Ticket<?> ticket) {
        if (!isTicketType(ticket.getType().toString())) {
            return null;
        }
        ChunkPos target = new ChunkPos(chunkPos);
        int radius = radius(serverViewDistance(level));
        List<PerfAttributionSnapshot.Bucket> buckets = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (covers(player.chunkPosition(), target, radius)) {
                buckets.add(bucket(level, player));
            }
        }
        return buckets.isEmpty() ? List.of(PerfAttributionSnapshot.UNKNOWN) : buckets;
    }

    static boolean isTicketType(String typeName) {
        return TICKET_TYPE.equals(typeName);
    }

    static boolean isViewDistanceSource(PerfAttributionSnapshot.Source source) {
        return isTicketType(source.name()) && source.id().contains(SOURCE_ID_MARKER);
    }

    static int radius(int serverViewDistance) {
        return serverViewDistance + VIEW_DISTANCE_PADDING;
    }

    static boolean covers(ChunkPos source, ChunkPos target, int radius) {
        long deltaX = Math.abs((long) source.x - target.x);
        long deltaZ = Math.abs((long) source.z - target.z);
        return deltaX <= radius && deltaZ <= radius;
    }

    private static PerfAttributionSnapshot.Bucket bucket(ServerLevel level, ServerPlayer player) {
        String sourceId = level.dimension().location() + ":" + TICKET_TYPE + ":" + player.getUUID();
        var source = new PerfAttributionSnapshot.Source(
                sourceId, TICKET_TYPE, PlayerAttribution.position(level, player.blockPosition()));
        return new PerfAttributionSnapshot.Bucket(PlayerAttribution.online(player), source, 0, 0);
    }

    private static int serverViewDistance(ServerLevel level) {
        return ((ChunkMapAccessor) (Object) level.getChunkSource().chunkMap).getServerViewDistance();
    }
}
