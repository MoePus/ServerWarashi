package com.moepus.serverwarashi.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DistanceManager.class, remap = false)
public interface DistanceManagerAccessor {
    @Accessor
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getTickets();

    @Accessor
    DistanceManager.ChunkTicketTracker getTicketTracker();

    @Accessor
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getForcedTickets();

    @Accessor
    TickingTracker getTickingTicketsTracker();

    @Invoker("getTicketDebugString")
    String invokeGetTicketDebugString(long chunkPos);
}
