package com.moepus.serverwarashi.common.modules;

import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.modules.cost.ChunkCostSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * TicketPerf 与 resident cost 共用的 tick 计时钩子：无分析、无采样时直接回落到原始调用。
 * 因被两个模块共用而放在 modules 根，不归属任一子包。
 * 层级：入口层（三个计时 mixin 的统一汇聚点）。
 * 上游：mixin/ticktiming。下游：TicketPerfRuntime / ChunkCostSampler。
 */
public final class TickTimingHooks {
    private TickTimingHooks() {
    }

    // 实体 tick 挂点：分析会话期间对被跟踪 chunk 换成计时包装，否则按需换成驻留成本采样包装
    public static <T extends Entity> Consumer<T> wrapEntityTickConsumer(ServerLevel level, Consumer<T> original, T entity) {
        if (TicketPerfRuntime.hasActiveSessionIn(level)) {
            BlockPos entityPos = entity.blockPosition();
            long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, entityPos);
            if (sessionId == TicketPerfRuntime.NO_SESSION_ID) {
                return original;
            }
            return new ProfilingEntityConsumer<>(original, entity, entityPos, sessionId);
        }

        if (!ChunkCostSampler.enabled()) {
            return original;
        }

        BlockPos entityPos = entity.blockPosition();
        if (!ChunkCostSampler.shouldSample(level, entityPos)) {
            return original;
        }
        return new ResidentCostEntityConsumer<>(level, original, entityPos);
    }

    // 方块实体 tick 挂点：分析会话期间换成计时迭代器，否则按需换成驻留成本采样迭代器
    public static Iterator<TickingBlockEntity> wrapBlockEntityIterator(ServerLevel serverLevel, Iterator<TickingBlockEntity> original) {
        if (TicketPerfRuntime.hasActiveSessionIn(serverLevel)) {
            return new ProfilingBlockEntityIterator(serverLevel, original);
        }
        if (!ChunkCostSampler.enabled()) {
            return original;
        }
        return new ResidentCostBlockEntityIterator(serverLevel, original);
    }

    // 区块 tick 挂点：分析会话期间对被跟踪 chunk 计时，否则按需走驻留成本采样
    public static void profileChunkTick(ServerLevel level,
                                        LevelChunk chunk,
                                        int randomTickSpeed,
                                        ChunkCostSampler.ChunkTickInvoker original) {
        if (TicketPerfRuntime.hasActiveSessionIn(level)) {
            ChunkPos pos = chunk.getPos();
            long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, pos.getWorldPosition());
            if (sessionId == TicketPerfRuntime.NO_SESSION_ID) {
                original.tick(level, chunk, randomTickSpeed);
                return;
            }

            long start = System.nanoTime();
            original.tick(level, chunk, randomTickSpeed);
            long duration = System.nanoTime() - start;
            TicketPerfRuntime.onChunkTick(pos, duration, sessionId);
            return;
        }

        if (ChunkCostSampler.enabled()) {
            ChunkCostSampler.tickChunk(level, chunk, randomTickSpeed, original);
            return;
        }
        original.tick(level, chunk, randomTickSpeed);
    }

    private record ProfilingEntityConsumer<T extends Entity>(Consumer<T> delegate, T entity,
                                                             BlockPos entityPos,
                                                             long sessionId) implements Consumer<T> {
        @Override
        public void accept(T value) {
            long start = System.nanoTime();
            delegate.accept(value);
            long duration = System.nanoTime() - start;
            if (TicketPerfRuntime.isAllOwnersSession()) {
                TicketPerfRuntime.onEntityTick(
                        entityPos,
                        null,
                        null,
                        null,
                        duration,
                        false,
                        sessionId
                );
            } else {
                String entityType = entity.getType().toString();
                String entityId = entity.getUUID().toString();
                String shortUuid = entityId.length() > 8 ? entityId.substring(0, 8) : entityId;
                String entityLabel = entityType + "#" + shortUuid + "@(" + entityPos.getX() + "," + entityPos.getY() + "," + entityPos.getZ() + ")";
                TicketPerfRuntime.onEntityTick(
                        entityPos,
                        entityType,
                        entityId,
                        entityLabel,
                        duration,
                        false,
                        sessionId
                );
            }
        }
    }

    private record ResidentCostEntityConsumer<T extends Entity>(ServerLevel level,
                                                                Consumer<T> delegate,
                                                                BlockPos entityPos) implements Consumer<T> {
        @Override
        public void accept(T value) {
            long start = System.nanoTime();
            delegate.accept(value);
            ChunkCostSampler.recordEntitySample(level, entityPos, System.nanoTime() - start);
        }
    }

    private record ProfilingBlockEntityIterator(ServerLevel level,
                                                Iterator<TickingBlockEntity> delegate) implements Iterator<TickingBlockEntity> {

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public TickingBlockEntity next() {
            TickingBlockEntity next = delegate.next();
            BlockPos pos = next.getPos();

            long sessionId = TicketPerfRuntime.resolveTrackSessionId(level, pos);
            if (sessionId == TicketPerfRuntime.NO_SESSION_ID) {
                return next;
            }

            return new ProfilingBlockEntity(next, pos, sessionId);
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    private record ResidentCostBlockEntityIterator(ServerLevel level,
                                                   Iterator<TickingBlockEntity> delegate)
            implements Iterator<TickingBlockEntity> {

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public TickingBlockEntity next() {
            TickingBlockEntity next = delegate.next();
            BlockPos pos = next.getPos();
            if (!ChunkCostSampler.shouldSample(level, pos)) {
                return next;
            }
            return new ResidentCostBlockEntity(level, next, pos);
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    private record ProfilingBlockEntity(TickingBlockEntity delegate, BlockPos pos,
                                        long sessionId) implements TickingBlockEntity {

        @Override
        public void tick() {
            long start = System.nanoTime();
            delegate.tick();
            long duration = System.nanoTime() - start;
            if (TicketPerfRuntime.isAllOwnersSession()) {
                TicketPerfRuntime.onEntityTick(
                        pos,
                        null,
                        null,
                        null,
                        duration,
                        true,
                        sessionId
                );
            } else {
                String blockEntityType = delegate.getType();
                String sourceId = Long.toString(pos.asLong());
                String sourceLabel = blockEntityType + "@(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
                TicketPerfRuntime.onEntityTick(
                        pos,
                        blockEntityType,
                        sourceId,
                        sourceLabel,
                        duration,
                        true,
                        sessionId
                );
            }
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public @NotNull BlockPos getPos() {
            return delegate.getPos();
        }

        @Override
        public @NotNull String getType() {
            return delegate.getType();
        }
    }

    private record ResidentCostBlockEntity(ServerLevel level,
                                           TickingBlockEntity delegate,
                                           BlockPos pos) implements TickingBlockEntity {

        @Override
        public void tick() {
            long start = System.nanoTime();
            delegate.tick();
            ChunkCostSampler.recordBlockEntitySample(level, pos, System.nanoTime() - start);
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public @NotNull BlockPos getPos() {
            return delegate.getPos();
        }

        @Override
        public @NotNull String getType() {
            return delegate.getType();
        }
    }
}
