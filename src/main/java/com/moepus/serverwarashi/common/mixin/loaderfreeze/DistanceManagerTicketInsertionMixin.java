package com.moepus.serverwarashi.common.mixin.loaderfreeze;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseBatch;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderFreezeService;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketContext;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DistanceManager.class, remap = false)
public abstract class DistanceManagerTicketInsertionMixin {
    @WrapOperation(
            method = "addTicket(JLnet/minecraft/server/level/Ticket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SortedArraySet;addOrGet(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object serverWarashi$applyLoaderPolicyBeforeInsertion(
            SortedArraySet<Ticket<?>> tickets, Object rawCandidate, Operation<Object> original,
            long chunkPos, Ticket<?> methodCandidate) {
        ServerLevel level = LoaderTicketContext.currentLevel();
        if (level == null || rawCandidate != methodCandidate) {
            return original.call(tickets, rawCandidate);
        }
        Ticket<?> candidate = methodCandidate;
        var preparation = LoaderFreezeService.prepareInsertion(level, candidate);
        Object rawStored = original.call(tickets, rawCandidate);
        if (preparation.isEmpty() || !(rawStored instanceof Ticket<?> stored)) {
            return rawStored;
        }

        var completion = LoaderFreezeService.completeInsertion(level, stored, preparation.get());
        if (completion.inserted()) {
            TicketPerfRuntime.onLoaderTicketInserted(
                    level, completion.ref(), new LoaderTicketTarget(chunkPos, stored));
        }
        if (!completion.inserted() && completion.storedPauseChanged()) {
            serverWarashi$refreshChunk(chunkPos);
        }
        return rawStored;
    }

    @Unique
    private void serverWarashi$refreshChunk(long chunkPos) {
        LongOpenHashSet chunks = new LongOpenHashSet();
        chunks.add(chunkPos);
        TicketPauseBatch.updateChunkLevel((DistanceManagerAccessor) this, chunks);
    }
}
