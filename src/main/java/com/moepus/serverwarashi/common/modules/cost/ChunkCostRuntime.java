package com.moepus.serverwarashi.common.modules.cost;

import com.moepus.serverwarashi.common.config.OptimizationConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.WeakHashMap;

/**
 * 低频驻留成本采样器，按 chunk 聚合 chunk tick、block entity tick 与 entity tick 成本。
 */
public final class ChunkCostRuntime {
    private static final int SAMPLE_PERIOD_TICKS = 40;
    // 给下一轮采样 tick 与 ServerTick.Post HUD 推送留一个 tick 宽限，避免刚到采样窗口就提前过期。
    private static final long SAMPLE_STALE_TICKS = SAMPLE_PERIOD_TICKS + 1L;
    private static final int EWMA_OLD_WEIGHT = 7;
    private static final long UNKNOWN_COST_NANOS = -1L;
    private static final long NO_SAMPLE_GAME_TIME = Long.MIN_VALUE;
    private static final CostSample UNKNOWN_COST = new CostSample(
            UNKNOWN_COST_NANOS,
            UNKNOWN_COST_NANOS,
            UNKNOWN_COST_NANOS
    );
    private static final WeakHashMap<ServerLevel, Long2ObjectOpenHashMap<MutableChunkCost>> COST_BY_LEVEL =
            new WeakHashMap<>();
    // 采样写路径默认仅主线程调用；但注入点（tickChunk redirect）可能被并行 tick 的 mod 搬到 worker 线程，
    // 故对所有访问加锁，防止 WeakHashMap / fastutil map 并发 rehash 死循环（见 bug-03）。
    private static final Object SAMPLE_LOCK = new Object();

    private ChunkCostRuntime() {
    }

    public static void tickChunk(ServerLevel level,
                                 LevelChunk chunk,
                                 int randomTickSpeed,
                                 ChunkTickInvoker original) {
        // 调用点（hooks）已先判 enabled()
        if (!shouldSample(level.getGameTime(), chunk.getPos().toLong())) {
            original.tick(level, chunk, randomTickSpeed);
            return;
        }
        long start = System.nanoTime();
        original.tick(level, chunk, randomTickSpeed);
        recordChunkSample(level, chunk.getPos().toLong(), System.nanoTime() - start);
    }

    public static CostSample costSampleForChunk(ServerLevel level, long chunkPos) {
        long currentGameTime = level.getGameTime();
        synchronized (SAMPLE_LOCK) {
            Long2ObjectOpenHashMap<MutableChunkCost> costs = COST_BY_LEVEL.get(level);
            MutableChunkCost cost = costs == null ? null : costs.get(chunkPos);
            return cost == null ? UNKNOWN_COST : cost.snapshot(currentGameTime);
        }
    }

    public static boolean hasCostData(ServerLevel level) {
        if (!enabled()) {
            return false;
        }
        synchronized (SAMPLE_LOCK) {
            Long2ObjectOpenHashMap<MutableChunkCost> costs = COST_BY_LEVEL.get(level);
            return costs != null && !costs.isEmpty();
        }
    }

    public static boolean enabled() {
        return OptimizationConfig.residentChunkCostEnabled();
    }

    public static boolean shouldSample(ServerLevel level, BlockPos pos) {
        return shouldSample(level.getGameTime(), ChunkPos.asLong(pos));
    }

    public static void recordEntitySample(ServerLevel level, BlockPos pos, long durationNanos) {
        long gameTime = level.getGameTime();
        synchronized (SAMPLE_LOCK) {
            costEntry(level, ChunkPos.asLong(pos)).recordEntity(gameTime, durationNanos);
        }
    }

    public static void recordBlockEntitySample(ServerLevel level, BlockPos pos, long durationNanos) {
        long gameTime = level.getGameTime();
        synchronized (SAMPLE_LOCK) {
            costEntry(level, ChunkPos.asLong(pos)).recordBlockEntity(gameTime, durationNanos);
        }
    }

    private static boolean shouldSample(long gameTime, long chunkPos) {
        // 错峰采样，使不同 chunk 落在不同 tick。
        return Math.floorMod(gameTime + chunkPos, SAMPLE_PERIOD_TICKS) == 0;
    }

    private static void recordChunkSample(ServerLevel level, long chunkPos, long durationNanos) {
        long gameTime = level.getGameTime();
        synchronized (SAMPLE_LOCK) {
            costEntry(level, chunkPos).recordChunk(gameTime, durationNanos);
        }
    }

    private static MutableChunkCost costEntry(ServerLevel level, long chunkPos) {
        Long2ObjectOpenHashMap<MutableChunkCost> costs = COST_BY_LEVEL.computeIfAbsent(level, ignored -> new Long2ObjectOpenHashMap<>());
        return costs.computeIfAbsent(chunkPos, ignored -> new MutableChunkCost());
    }

    private static long updateEwma(long previous, long durationNanos) {
        return previous == UNKNOWN_COST_NANOS
                ? durationNanos
                : (previous * EWMA_OLD_WEIGHT + durationNanos) / (EWMA_OLD_WEIGHT + 1L);
    }

    private static long currentNanos(long nanos, long sampleGameTime, long currentGameTime) {
        if (nanos == UNKNOWN_COST_NANOS) {
            return UNKNOWN_COST_NANOS;
        }
        return sampleStale(sampleGameTime, currentGameTime) ? UNKNOWN_COST_NANOS : nanos;
    }

    private static boolean sampleStale(long sampleGameTime, long currentGameTime) {
        return sampleGameTime != NO_SAMPLE_GAME_TIME
                && currentGameTime - sampleGameTime > SAMPLE_STALE_TICKS;
    }

    public record CostSample(long chunkNanos, long blockEntityNanos, long entityNanos) {
        public long totalNanos() {
            if (!hasKnownCost()) {
                return UNKNOWN_COST_NANOS;
            }
            return knownNanos(chunkNanos) + knownNanos(blockEntityNanos) + knownNanos(entityNanos);
        }

        public boolean hasKnownCost() {
            return chunkNanos != UNKNOWN_COST_NANOS
                    || blockEntityNanos != UNKNOWN_COST_NANOS
                    || entityNanos != UNKNOWN_COST_NANOS;
        }

        static long knownNanos(long value) {
            return value == UNKNOWN_COST_NANOS ? 0L : value;
        }
    }

    private static final class MutableChunkCost {
        private long chunkNanos = UNKNOWN_COST_NANOS;
        private long chunkSampleGameTime = NO_SAMPLE_GAME_TIME;
        private final PendingComponentCost blockEntityCost = new PendingComponentCost();
        private final PendingComponentCost entityCost = new PendingComponentCost();

        private void recordChunk(long gameTime, long durationNanos) {
            if (sampleStale(chunkSampleGameTime, gameTime)) {
                chunkNanos = UNKNOWN_COST_NANOS;
            }
            chunkNanos = updateEwma(chunkNanos, durationNanos);
            chunkSampleGameTime = gameTime;
        }

        private void recordBlockEntity(long gameTime, long durationNanos) {
            blockEntityCost.record(gameTime, durationNanos);
        }

        private void recordEntity(long gameTime, long durationNanos) {
            entityCost.record(gameTime, durationNanos);
        }

        private CostSample snapshot(long currentGameTime) {
            return new CostSample(
                    currentNanos(chunkNanos, chunkSampleGameTime, currentGameTime),
                    blockEntityCost.snapshot(currentGameTime),
                    entityCost.snapshot(currentGameTime)
            );
        }
    }

    private static final class PendingComponentCost {
        private static final long NO_PENDING_TICK = Long.MIN_VALUE;

        private long committedNanos = UNKNOWN_COST_NANOS;
        private long committedGameTime = NO_SAMPLE_GAME_TIME;
        private long pendingGameTime = NO_PENDING_TICK;
        private long pendingNanos;

        private void record(long gameTime, long durationNanos) {
            if (pendingGameTime == NO_PENDING_TICK) {
                pendingGameTime = gameTime;
                pendingNanos = durationNanos;
                return;
            }
            if (pendingGameTime != gameTime) {
                if (sampleStale(pendingGameTime, gameTime)) {
                    committedNanos = UNKNOWN_COST_NANOS;
                    committedGameTime = NO_SAMPLE_GAME_TIME;
                    pendingGameTime = gameTime;
                    pendingNanos = durationNanos;
                    return;
                }
                commitPending();
                pendingGameTime = gameTime;
                pendingNanos = durationNanos;
                return;
            }
            pendingNanos += durationNanos;
        }

        private long snapshot(long currentGameTime) {
            if (pendingGameTime == NO_PENDING_TICK) {
                return currentNanos(committedNanos, committedGameTime, currentGameTime);
            }
            long nanos = updateEwma(committedNanos, pendingNanos);
            return currentNanos(nanos, pendingGameTime, currentGameTime);
        }

        private void commitPending() {
            if (sampleStale(committedGameTime, pendingGameTime)) {
                committedNanos = UNKNOWN_COST_NANOS;
            }
            committedNanos = updateEwma(committedNanos, pendingNanos);
            committedGameTime = pendingGameTime;
        }
    }

    @FunctionalInterface
    public interface ChunkTickInvoker {
        void tick(ServerLevel level, LevelChunk chunk, int randomTickSpeed);
    }
}
