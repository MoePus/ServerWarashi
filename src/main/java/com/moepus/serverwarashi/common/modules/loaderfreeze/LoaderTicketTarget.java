package com.moepus.serverwarashi.common.modules.loaderfreeze;

import net.minecraft.server.level.Ticket;

/** A server-only ticket reference captured with the chunk containing it. */
public record LoaderTicketTarget(long centerChunkPos, Ticket<?> ticket) {
    public LoaderTicketTarget {
        if (ticket == null) {
            throw new IllegalArgumentException("ticket cannot be null");
        }
    }
}
