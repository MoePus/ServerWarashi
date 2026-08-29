package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.util.PackedPositionHash;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class TickSamples {
    final Map<TickableKey, TickCount> tickables = new HashMap<>();
    final Map<ChunkKey, Integer> chunks = new HashMap<>();
    PerfAttributionSnapshot attribution;
    long durationNanos;
    long completedAtNanos;
    long gcStartMillis;
    long gcTimeMillis;
    int totalSamples;
    boolean refreshReport;

    TickSamples(PerfAttributionSnapshot attribution) {
        this.attribution = attribution;
    }

    void sample(PerfWorkTracker.Snapshot snapshot) {
        totalSamples++;
        if (snapshot == null) {
            return;
        }
        TickableKey key = TickableKey.from(snapshot);
        tickables.computeIfAbsent(key, ignored -> new TickCount()).record(snapshot);
        ChunkKey chunk = ChunkKey.from(snapshot);
        if (chunk != null) {
            chunks.merge(chunk, 1, Integer::sum);
        }
    }

    void mergeSamples(TickSamples source) {
        totalSamples += source.totalSamples;
        source.tickables.forEach((key, count) ->
                tickables.computeIfAbsent(key, ignored -> new TickCount()).merge(count));
        source.chunks.forEach((key, count) -> chunks.merge(key, count, Integer::sum));
    }

    record ChunkKey(String dimension, long position) {
        static ChunkKey from(PerfWorkTracker.Snapshot snapshot) {
            if (!snapshot.positioned() || snapshot.dimension() == null) {
                return null;
            }
            long position = snapshot.chunkPosition()
                    ? ChunkPos.asLong(snapshot.x(), snapshot.z())
                    : ChunkPos.asLong(snapshot.x() >> 4, snapshot.z() >> 4);
            return new ChunkKey(snapshot.dimension().toString(), position);
        }

        @Override
        public int hashCode() {
            return 31 * dimension.hashCode() + PackedPositionHash.hash(position);
        }
    }

    record TickableKey(TickType type,
                       String dimension,
                       Class<?> workClass,
                       ResourceLocation detailId,
                       TickDetailKind detailKind,
                       int entityId,
                       long fixedPosition,
                       String eventLoop,
                       String displayName) {
        static TickableKey from(PerfWorkTracker.Snapshot snapshot) {
            String dimension = snapshot.dimension() == null ? "" : snapshot.dimension().toString();
            long position = switch (snapshot.type()) {
                case CHUNK -> ChunkPos.asLong(snapshot.x(), snapshot.z());
                case BLOCK_ENTITY -> BlockPos.asLong(snapshot.x(), snapshot.y(), snapshot.z());
                case ENTITY, TASK, HANDLER, GC -> 0L;
            };
            return new TickableKey(snapshot.type(), dimension, snapshot.workClass(), snapshot.detailId(),
                    snapshot.detailKind(),
                    snapshot.entityId(), position, snapshot.eventLoop(), snapshot.displayName());
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + dimension.hashCode();
            result = 31 * result + workClass.hashCode();
            result = 31 * result + Objects.hashCode(detailId);
            result = 31 * result + detailKind.hashCode();
            result = 31 * result + entityId;
            result = 31 * result + PackedPositionHash.hash(fixedPosition);
            result = 31 * result + Objects.hashCode(eventLoop);
            return 31 * result + Objects.hashCode(displayName);
        }
    }

    static final class TickCount {
        int count;
        boolean positioned;
        boolean chunkPosition;
        int x;
        int y;
        int z;

        void record(PerfWorkTracker.Snapshot snapshot) {
            count++;
            positioned = snapshot.positioned();
            chunkPosition = snapshot.chunkPosition();
            x = snapshot.x();
            y = snapshot.y();
            z = snapshot.z();
        }

        void merge(TickCount source) {
            count += source.count;
            positioned = source.positioned;
            chunkPosition = source.chunkPosition;
            x = source.x;
            y = source.y;
            z = source.z;
        }
    }
}
