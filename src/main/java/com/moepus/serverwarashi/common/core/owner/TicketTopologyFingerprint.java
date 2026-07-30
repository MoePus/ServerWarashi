package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

final class TicketTopologyFingerprint {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private long value = FNV_OFFSET_BASIS;

    static long capture(MinecraftServer server) {
        TicketTopologyFingerprint fingerprint = new TicketTopologyFingerprint();
        for (ServerLevel level : server.getAllLevels()) {
            fingerprint.addDimension(level);
            TicketScanService.visitTicketChunks(level, fingerprint::addTickets);
        }
        return fingerprint.value();
    }

    void addDimension(ServerLevel level) {
        add(level.dimension().location().hashCode());
    }

    void addTickets(long chunkPos, Iterable<? extends Ticket<?>> tickets) {
        boolean chunkAdded = false;
        for (Ticket<?> ticket : tickets) {
            if (!isPotentialLoaderTicket(ticket)) {
                continue;
            }
            if (!chunkAdded) {
                add(chunkPos);
                chunkAdded = true;
            }
            add(System.identityHashCode(ticket));
            add(ticket.getTicketLevel());
        }
    }

    long value() {
        return value;
    }

    private void add(long part) {
        value ^= part;
        value *= FNV_PRIME;
    }

    private static boolean isPotentialLoaderTicket(Ticket<?> ticket) {
        return ticket.getType() != TicketType.PLAYER
                && ticket.getType() != TicketType.START
                && ticket.getType() != TicketType.PORTAL
                && ticket.getType() != TicketType.POST_TELEPORT
                && ticket.getType() != TicketType.DRAGON
                && ticket.getType() != TicketType.UNKNOWN;
    }
}
