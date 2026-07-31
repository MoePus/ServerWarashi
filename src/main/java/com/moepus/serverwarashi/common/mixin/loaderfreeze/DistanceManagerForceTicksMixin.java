package com.moepus.serverwarashi.common.mixin.loaderfreeze;

import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderFreezeService;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerForceTicksMixin {
    @Shadow
    @Final
    private Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> forcedTickets;

    @Inject(method = "shouldForceTicks(J)Z", at = @At("HEAD"), cancellable = true)
    private void serverWarashi$ignorePausedForcedTickets(long chunkPos, CallbackInfoReturnable<Boolean> callback) {
        SortedArraySet<Ticket<?>> tickets = forcedTickets.get(chunkPos);
        callback.setReturnValue(tickets != null && LoaderFreezeService.hasUnpausedTicket(tickets));
    }
}
