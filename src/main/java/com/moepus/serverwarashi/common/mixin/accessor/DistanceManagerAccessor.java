package com.moepus.serverwarashi.common.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DistanceManager.class, remap = false)

public interface DistanceManagerAccessor {
    @Accessor
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> getTickets();

    // 仅 src/test 审计基建（AuditChunkFacts 玩家泡判定）使用；泡语义须与区块加载注册一致，
    // 不能用 level.players() 替代（gametest 假人只注册进 playersPerChunk）
    @Accessor
    Long2ObjectMap<ObjectSet<ServerPlayer>> getPlayersPerChunk();

    @Accessor
    DistanceManager.ChunkTicketTracker getTicketTracker();

    @Accessor
    TickingTracker getTickingTicketsTracker();
}
