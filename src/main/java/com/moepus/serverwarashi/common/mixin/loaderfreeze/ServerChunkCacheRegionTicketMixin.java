package com.moepus.serverwarashi.common.mixin.loaderfreeze;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketContext;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ServerChunkCacheRegionTicketMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @WrapOperation(
            method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V"
            )
    )
    private <T> void serverWarashi$withOwningLevel(
            DistanceManager manager, TicketType<T> type, ChunkPos pos, int distance, T value,
            boolean forceTicks, Operation<Void> original) {
        LoaderTicketContext.run(level, () -> original.call(manager, type, pos, distance, value, forceTicks));
    }
}
