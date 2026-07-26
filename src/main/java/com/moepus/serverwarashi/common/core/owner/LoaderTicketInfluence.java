package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongConsumer;

final class LoaderTicketInfluence {
    private static final int MAX_INFLUENCE_RADIUS = 32;

    private LoaderTicketInfluence() {
    }

    static int blockTickingRadius(Ticket<?> ticket) {
        return blockTickingRadius(TicketUtils.getOriginLevel(ticket));
    }

    static int blockTickingRadius(int ticketLevel) {
        long radius = (long) TicketUtils.MAX_PAUSEABLE_LEVEL - ticketLevel;
        return (int) Math.max(0L, Math.min(MAX_INFLUENCE_RADIUS, radius));
    }

    static void visit(long center, int radius, LongConsumer consumer) {
        ChunkPos position = new ChunkPos(center);
        long minX = Math.max(Integer.MIN_VALUE, (long) position.x - radius);
        long maxX = Math.min(Integer.MAX_VALUE, (long) position.x + radius);
        long minZ = Math.max(Integer.MIN_VALUE, (long) position.z - radius);
        long maxZ = Math.min(Integer.MAX_VALUE, (long) position.z + radius);
        for (long z = minZ; z <= maxZ; z++) {
            for (long x = minX; x <= maxX; x++) {
                consumer.accept(ChunkPos.asLong((int) x, (int) z));
            }
        }
    }
}
