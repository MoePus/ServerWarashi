package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.ChunkMapAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class C2meNoTickAttribution {
    static final String TICKET_TYPE = "c2me_notickvd";
    private static final String SOURCE_ID_MARKER = ":" + TICKET_TYPE + ":";
    private static final int VIEW_DISTANCE_PADDING = 1;

    private C2meNoTickAttribution() {
    }

    static CaptureContext captureContext(ServerLevel level, CaptureTarget target) {
        int radius = radius(serverViewDistance(level));
        List<PlayerRange> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos chunk = player.chunkPosition();
            players.add(new PlayerRange(chunk.x, chunk.z, bucket(level, player)));
        }
        return new CaptureContext(radius, players, target);
    }

    static boolean isTicket(Ticket<?> ticket) {
        return isTicketType(ticket.getType().toString());
    }

    static boolean capture(CaptureContext context, long chunkPos, Ticket<?> ticket) {
        if (!isTicket(ticket)) {
            return false;
        }
        if (context != null && !TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
            context.capture(chunkPos);
        }
        return true;
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

    record CaptureTarget(String dimension, Map<ChunkKey, Set<PerfAttributionSnapshot.Bucket>> chunks) {
    }

    record PlayerRange(int chunkX, int chunkZ, PerfAttributionSnapshot.Bucket bucket) {
        boolean covers(long chunkPos, int radius) {
            long deltaX = Math.abs((long) chunkX - ChunkPos.getX(chunkPos));
            long deltaZ = Math.abs((long) chunkZ - ChunkPos.getZ(chunkPos));
            return deltaX <= radius && deltaZ <= radius;
        }
    }

    static final class CaptureContext {
        private final int radius;
        private final List<PlayerRange> players;
        private final CaptureTarget target;

        CaptureContext(int radius, List<PlayerRange> players, CaptureTarget target) {
            this.radius = radius;
            this.players = List.copyOf(players);
            this.target = target;
        }

        void capture(long chunkPos) {
            boolean matched = false;
            for (PlayerRange player : players) {
                if (player.covers(chunkPos, radius)) {
                    add(chunkPos, player.bucket);
                    matched = true;
                }
            }
            if (!matched) {
                add(chunkPos, PerfAttributionSnapshot.UNKNOWN);
            }
        }

        private void add(long chunkPos, PerfAttributionSnapshot.Bucket bucket) {
            target.chunks.computeIfAbsent(new ChunkKey(target.dimension, chunkPos),
                    ignored -> new LinkedHashSet<>()).add(bucket);
        }
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
