package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Collects active, pauseable ticket chunks for watchdog diagnostics. */
public final class OwnerChunks {
    private OwnerChunks() {
    }

    public static Map<TicketOwner, Set<Long>> collectActive(ServerLevel level) {
        Map<TicketOwner, Set<Long>> owners = new HashMap<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            Ticket<?> fallback = null;
            boolean hasOwner = false;
            for (Ticket<?> ticket : tickets) {
                if (TicketUtils.getOriginLevel(ticket) > TicketUtils.MAX_PAUSEABLE_LEVEL) {
                    continue;
                }
                if (TicketUtils.isChunkProtectionTicket(ticket)) {
                    if (fallback == null || (fallback.getType() != TicketType.FORCED
                            && ticket.getType() == TicketType.FORCED)) {
                        fallback = ticket;
                    }
                    continue;
                }
                TicketOwner owner = TicketOwnerResolver.resolve(ticket, level);
                if (TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.name())) {
                    continue;
                }
                hasOwner = true;
                if (!TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
                    add(owners, owner, chunkPos);
                }
            }
            if (!hasOwner && fallback != null) {
                TicketOwner owner = TicketOwnerResolver.resolve(fallback, level);
                if (!TicketOwnerResolver.UNKNOWN_ENTITY.equals(owner.name())) {
                    add(owners, owner, chunkPos);
                }
            }
        });
        return owners;
    }

    private static void add(Map<TicketOwner, Set<Long>> owners, TicketOwner owner, long chunkPos) {
        owners.computeIfAbsent(owner, ignored -> new LongOpenHashSet()).add(chunkPos);
    }
}
