package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.util.PackedPositionHash;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record PerfWorldHotspotIndex(long streamEpoch,
                                    long revision,
                                    long tickCount,
                                    long nanosPerSample,
                                    List<Entry> entries) {
    public static final int MAX_SAMPLES_SCALE = 256;

    public PerfWorldHotspotIndex {
        if (streamEpoch < 0L || revision < 0L || tickCount < 0L || nanosPerSample < 0L) {
            throw new IllegalArgumentException("Hotspot index scalars must be non-negative");
        }
        entries = List.copyOf(entries);
    }

    public static PerfWorldHotspotIndex empty(long streamEpoch, long revision, long tickCount) {
        return new PerfWorldHotspotIndex(streamEpoch, revision, tickCount, 0L, List.of());
    }

    public record Cost(long samples, int maxSamplesQ8) {
        public Cost {
            if (samples < 0L || maxSamplesQ8 < 0) {
                throw new IllegalArgumentException("Hotspot samples must be non-negative");
            }
        }

        public PerfReportData.Metric metric(long tickCount, long nanosPerSample) {
            if (tickCount <= 0L || nanosPerSample <= 0L) {
                return new PerfReportData.Metric(0.0D, 0.0D, samples);
            }
            double millisPerSample = nanosPerSample / 1_000_000.0D;
            double averageMillis = samples * millisPerSample / tickCount;
            double maximumMillis = maxSamplesQ8 / (double) MAX_SAMPLES_SCALE * millisPerSample;
            return new PerfReportData.Metric(
                    averageMillis, Math.max(averageMillis, maximumMillis), samples);
        }
    }

    public record Entry(Key key,
                        String dimension,
                        int x,
                        int y,
                        int z,
                        TickDetailKind detailKind,
                        ResourceLocation detailId,
                        long samples,
                        int maxSamplesQ8) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(detailKind, "detailKind");
            Objects.requireNonNull(detailId, "detailId");
            if (samples < 0L || maxSamplesQ8 < 0) {
                throw new IllegalArgumentException("Hotspot samples must be non-negative");
            }
            key.validateDetailKind(detailKind);
            if (key.kind() == Kind.BLOCK_ENTITY
                    && key.blockPosition() != BlockPos.asLong(x, y, z)) {
                throw new IllegalArgumentException("Block-entity hotspot position mismatch");
            }
        }

    }

    public record Key(Kind kind, int entityId, long blockPosition) {
        public Key {
            Objects.requireNonNull(kind, "kind");
            if (kind == Kind.ENTITY && (entityId < 0 || blockPosition != 0L)) {
                throw new IllegalArgumentException("Invalid entity hotspot key");
            }
            if (kind == Kind.BLOCK_ENTITY && entityId != -1) {
                throw new IllegalArgumentException("Invalid block-entity hotspot key");
            }
        }

        public static Key entity(int entityId) {
            return new Key(Kind.ENTITY, entityId, 0L);
        }

        public static Key blockEntity(int x, int y, int z) {
            return new Key(Kind.BLOCK_ENTITY, -1, BlockPos.asLong(x, y, z));
        }

        public void validateDetailKind(TickDetailKind detailKind) {
            boolean valid = kind == Kind.BLOCK_ENTITY
                    ? detailKind == TickDetailKind.BLOCK
                    : detailKind == TickDetailKind.ENTITY || detailKind == TickDetailKind.ITEM;
            if (!valid) {
                throw new IllegalArgumentException("Hotspot key and detail kind do not match");
            }
        }

        @Override
        public int hashCode() {
            int identityHash = kind == Kind.ENTITY
                    ? entityId : PackedPositionHash.hash(blockPosition);
            return 31 * kind.hashCode() + identityHash;
        }
    }

    public enum Kind {
        ENTITY,
        BLOCK_ENTITY
    }
}
