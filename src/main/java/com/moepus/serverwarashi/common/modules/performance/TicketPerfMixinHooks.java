package com.moepus.serverwarashi.common.modules.performance;

import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfSessionController;
import com.moepus.serverwarashi.common.modules.cost.ChunkCostRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * ChunkPerf 热路径钩子：在非分析状态下尽量直接回落到原始调用。
 */
public final class TicketPerfMixinHooks {
    private TicketPerfMixinHooks() {
    }

    // 实体 tick 挂点：分析会话期间对被跟踪 chunk 换成计时包装，否则按需换成驻留成本采样包装
    public static <T extends Entity> Consumer<T> wrapEntityTickConsumer(ServerLevel level, Consumer<T> original, T entity) {
        boolean hasActiveSession = TicketPerfSessionController.hasActiveSession(level);
        if (hasActiveSession) {
            BlockPos entityPos = entity.blockPosition();
            long sessionId = TicketPerfSessionController.resolveTrackSessionId(level, entityPos);
            if (sessionId < 0L) {
                return original;
            }
            return new ProfilingEntityConsumer<>(level, original, entity, entityPos, sessionId);
        }

        if (!ChunkCostRuntime.enabled()) {
            return original;
        }

        BlockPos entityPos = entity.blockPosition();
        if (!ChunkCostRuntime.shouldSample(level, entityPos)) {
            return original;
        }
        return new ResidentCostEntityConsumer<>(level, original, entityPos);
    }

    // 方块实体 tick 挂点：分析会话期间换成计时迭代器，否则按需换成驻留成本采样迭代器
    public static Iterator<TickingBlockEntity> wrapBlockEntityIterator(Level level, Iterator<TickingBlockEntity> original) {
        if (level.isClientSide) {
            return original;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return original;
        }
        if (TicketPerfSessionController.hasActiveSession(serverLevel)) {
            return new ProfilingBlockEntityIterator(level, original);
        }
        if (!ChunkCostRuntime.enabled()) {
            return original;
        }
        return new ResidentCostBlockEntityIterator(serverLevel, original);
    }

    // 区块 tick 挂点：分析会话期间对被跟踪 chunk 计时，否则按需走驻留成本采样
    public static void profileChunkTick(ServerLevel level,
                                        LevelChunk chunk,
                                        int randomTickSpeed,
                                        ChunkCostRuntime.ChunkTickInvoker original) {
        boolean hasActiveSession = TicketPerfSessionController.hasActiveSession(level);
        if (hasActiveSession) {
            ChunkPos pos = chunk.getPos();
            long sessionId = TicketPerfSessionController.resolveTrackSessionId(level, pos.getWorldPosition());
            if (sessionId < 0L) {
                original.tick(level, chunk, randomTickSpeed);
                return;
            }

            long start = System.nanoTime();
            original.tick(level, chunk, randomTickSpeed);
            long duration = System.nanoTime() - start;
            TicketPerfSessionController.onChunkTick(level, pos, duration, sessionId);
            return;
        }

        if (ChunkCostRuntime.enabled()) {
            ChunkCostRuntime.tickChunk(level, chunk, randomTickSpeed, original);
            return;
        }
        original.tick(level, chunk, randomTickSpeed);
    }

    private record ProfilingEntityConsumer<T extends Entity>(ServerLevel level, Consumer<T> delegate, T entity,
                                                             BlockPos entityPos,
                                                             long sessionId) implements Consumer<T> {
        @Override
        public void accept(T value) {
            long start = System.nanoTime();
            delegate.accept(value);
            long duration = System.nanoTime() - start;
            if (TicketPerfSessionController.isAllOwnersSession()) {
                TicketPerfSessionController.onEntityTick(
                        level,
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
                TicketPerfSessionController.onEntityTick(
                        level,
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
            ChunkCostRuntime.recordEntitySample(level, entityPos, System.nanoTime() - start);
        }
    }

    private record ProfilingBlockEntityIterator(Level level,
                                                Iterator<TickingBlockEntity> delegate) implements Iterator<TickingBlockEntity> {

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public TickingBlockEntity next() {
            TickingBlockEntity next = delegate.next();
            BlockPos pos = next.getPos();

            long sessionId = TicketPerfSessionController.resolveTrackSessionId(level, pos);
            if (sessionId < 0L) {
                return next;
            }

            return new ProfilingBlockEntity(level, next, pos, sessionId);
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
            if (!ChunkCostRuntime.shouldSample(level, pos)) {
                return next;
            }
            return new ResidentCostBlockEntity(level, next, pos);
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    private record ProfilingBlockEntity(Level level, TickingBlockEntity delegate, BlockPos pos,
                                        long sessionId) implements TickingBlockEntity {

        @Override
        public void tick() {
            long start = System.nanoTime();
            delegate.tick();
            long duration = System.nanoTime() - start;
            if (TicketPerfSessionController.isAllOwnersSession()) {
                TicketPerfSessionController.onEntityTick(
                        level,
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
                TicketPerfSessionController.onEntityTick(
                        level,
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
            ChunkCostRuntime.recordBlockEntitySample(level, pos, System.nanoTime() - start);
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
