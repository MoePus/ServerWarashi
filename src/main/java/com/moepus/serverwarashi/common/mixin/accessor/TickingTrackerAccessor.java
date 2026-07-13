package com.moepus.serverwarashi.common.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TickingTracker.class, remap = false)
public interface TickingTrackerAccessor {
    @Accessor
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getTickets();
}
