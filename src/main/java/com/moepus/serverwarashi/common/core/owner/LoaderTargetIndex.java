package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import net.minecraft.server.level.Ticket;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Live ticket references, guarded by the owning attribution snapshot's monitor. */
final class LoaderTargetIndex {
    // BlockLoaderRef mixes packed coordinates; ticket/key/ChunkPos hashCode is never used.
    private final Map<BlockLoaderRef, IdentityHashMap<Ticket<?>, LoaderTicketTarget>> byLoader = new HashMap<>();
    private final IdentityHashMap<Ticket<?>, BlockLoaderRef> byTicket = new IdentityHashMap<>();

    LoaderTargetIndex(Map<BlockLoaderRef, List<LoaderTicketTarget>> captured) {
        captured.forEach((ref, targets) -> {
            byLoader.put(ref, new IdentityHashMap<>(targets.size()));
            targets.forEach(target -> add(ref, target));
        });
    }

    List<LoaderTicketTarget> targets(BlockLoaderRef ref) {
        var targets = byLoader.get(ref);
        return targets == null ? List.of() : List.copyOf(targets.values());
    }

    boolean add(BlockLoaderRef ref, LoaderTicketTarget target) {
        var targets = byLoader.get(ref);
        if (targets == null || byTicket.containsKey(target.ticket())) {
            return false;
        }
        targets.put(target.ticket(), target);
        byTicket.put(target.ticket(), ref);
        return true;
    }

    boolean remove(Ticket<?> ticket) {
        BlockLoaderRef ref = byTicket.remove(ticket);
        if (ref == null) {
            return false;
        }
        // Keep the known loader entry so a later insertion can repopulate an empty loader.
        byLoader.get(ref).remove(ticket);
        return true;
    }
}
