package com.moepus.serverwarashi.common.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DistanceManager.class, remap = false)
@SuppressWarnings("unused")
public interface DistanceManagerAccessor {
    @Accessor
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getTickets();

    @Accessor
    Long2ObjectMap<ObjectSet<ServerPlayer>> getPlayersPerChunk();

    @Accessor
    DistanceManager.ChunkTicketTracker getTicketTracker();

    @Accessor
    TickingTracker getTickingTicketsTracker();
}
