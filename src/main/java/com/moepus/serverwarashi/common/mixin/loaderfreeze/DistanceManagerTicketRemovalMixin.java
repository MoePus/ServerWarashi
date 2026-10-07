package com.moepus.serverwarashi.common.mixin.loaderfreeze;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerTicketRemovalMixin {
    @WrapOperation(
            method = "removeTicket(JLnet/minecraft/server/level/Ticket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SortedArraySet;remove(Ljava/lang/Object;)Z",
                    ordinal = 0
            )
    )
    private boolean serverWarashi$forgetRemovedTicket(
            SortedArraySet<Ticket<?>> tickets, Object candidate, Operation<Boolean> original) {
        // removeRegionTicket creates an equal candidate; the index needs the actual stored object.
        Ticket<?> stored = tickets.get((Ticket<?>) candidate);
        boolean removed = original.call(tickets, candidate);
        if (removed && stored != null) {
            TicketPerfRuntime.onLoaderTicketRemoved(stored);
        }
        return removed;
    }

    @WrapOperation(
            method = {"purgeStaleTickets", "removeTicketsOnClosing"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/TickingTracker;removeTicket(JLnet/minecraft/server/level/Ticket;)V"
            ),
            require = 2
    )
    private void serverWarashi$forgetExpiredOrClosingTicket(
            TickingTracker tracker, long chunkPos, Ticket<?> stored, Operation<Void> original) {
        // Both iterator-removal paths pass the removed object to the ticking tracker.
        original.call(tracker, chunkPos, stored);
        TicketPerfRuntime.onLoaderTicketRemoved(stored);
    }
}
