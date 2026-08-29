package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DangerousChunkModel {
    private static final double LOWEST_COLOR_CONTRIBUTION = 0.25D;
    private static final HeatmapColorStop[] COLORS = {
            new HeatmapColorStop(200, 64, 245, 255),
            HeatmapRiskScale.GREEN.withAlpha(204),
            HeatmapRiskScale.YELLOW.withAlpha(208),
            HeatmapRiskScale.ORANGE.withAlpha(212),
            HeatmapRiskScale.RED.withAlpha(216),
            new HeatmapColorStop(224, 204, 70, 255)
    };

    private DangerousChunkModel() {
    }

    public static List<Chunk> build(PerfReportData report,
                                    WeakChunks weak,
                                    String dimension) {
        String weakDimension = weak == null ? null : weak.dimension();
        List<WeakChunkSnapshot.ChunkEntry> weakChunks = weak == null
                ? List.of() : weak.chunks();
        Long2ObjectMap<ChunkRisk> risks = new Long2ObjectLinkedOpenHashMap<>();
        if (report != null) {
            for (PerfReportData.ChunkEntry chunk : report.chunks()) {
                if (dimension.equals(chunk.dimension())) {
                    risks.computeIfAbsent(ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()),
                            ignored -> new ChunkRisk(chunk.chunkX(), chunk.chunkZ())).metric = chunk.metric();
                }
            }
        }
        if (dimension.equals(weakDimension)) {
            for (WeakChunkSnapshot.ChunkEntry chunk : weakChunks) {
                risks.computeIfAbsent(ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()),
                        ignored -> new ChunkRisk(chunk.chunkX(), chunk.chunkZ())).entityCount = chunk.entityCount();
            }
        }
        return risks.values().stream().map(ChunkRisk::build).filter(Objects::nonNull).toList();
    }

    public static int argb(float color) {
        double position = Mth.clamp(color, 0.0F, 1.0F) * (COLORS.length - 1);
        return HeatmapColorStop.interpolate(COLORS, position);
    }

    private static float colorLevel(double contribution) {
        double level = Math.log(contribution / LOWEST_COLOR_CONTRIBUTION)
                / Math.log(HeatmapRiskScale.PURPLE_CONTRIBUTION / LOWEST_COLOR_CONTRIBUTION);
        return (float) Mth.clamp(level, 0.0D, 1.0D);
    }

    public record Chunk(int chunkX, int chunkZ, int entityCount,
                        float color, double priority) {
    }

    public record WeakChunks(String dimension,
                             List<WeakChunkSnapshot.ChunkEntry> chunks) {
        public WeakChunks {
            chunks = List.copyOf(chunks);
        }

        public static WeakChunks from(WeakChunkSnapshot snapshot) {
            return snapshot == null ? null : new WeakChunks(snapshot.dimension(), snapshot.chunks());
        }
    }

    private static final class ChunkRisk {
        private final int chunkX;
        private final int chunkZ;
        private PerfReportData.Metric metric;
        private int entityCount;

        private ChunkRisk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private Chunk build() {
            double combined = ChunkHeatmapModel.combinedContribution(metric, entityCount);
            if (combined < HeatmapRiskScale.RED_CONTRIBUTION) {
                return null;
            }
            double weakScale = entityCount / (double) WeakChunkSnapshot.ENTITY_PURPLE_THRESHOLD
                    * HeatmapRiskScale.PURPLE_CONTRIBUTION;
            return new Chunk(chunkX, chunkZ, entityCount,
                    colorLevel(combined), Math.max(combined, weakScale));
        }
    }
}
