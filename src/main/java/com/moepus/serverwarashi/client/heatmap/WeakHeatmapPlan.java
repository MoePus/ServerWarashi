package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class WeakHeatmapPlan {
    private WeakHeatmapPlan() {
    }

    static ChunkHeatmapModel.Plan apply(ChunkHeatmapModel.Plan base,
                                        String weakDimension,
                                        List<WeakChunkSnapshot.ChunkEntry> weakChunks,
                                        String dimension) {
        if (weakChunks == null || weakChunks.isEmpty() || !dimension.equals(weakDimension)) {
            return base;
        }
        Map<Long, WeakChunkSnapshot.ChunkEntry> weakByChunk = indexChunks(weakChunks);
        List<ChunkHeatmapModel.Quad> quads = new ArrayList<>(base.quads().size() + weakByChunk.size());
        Map<Long, ChunkHeatmapModel.HoverInfo> hover = new HashMap<>(base.hover());
        boolean incomplete = base.hover().values().stream()
                .anyMatch(ChunkHeatmapModel.HoverInfo::incomplete);
        for (ChunkHeatmapModel.Quad quad : base.quads()) {
            long key = ChunkPos.asLong(quad.chunkX(), quad.chunkZ());
            WeakChunkSnapshot.ChunkEntry weak = weakByChunk.remove(key);
            if (weak == null) {
                quads.add(quad);
                continue;
            }
            ChunkHeatmapModel.HoverInfo info = hover.get(key);
            quads.add(new ChunkHeatmapModel.Quad(quad.chunkX(), quad.chunkZ(),
                    ChunkHeatmapModel.combinedColor(info == null ? null : info.metric(),
                            weak.entityCount())));
            hover.put(key, withWeak(info, weak));
        }
        addUnrepresented(weakByChunk, quads, hover, incomplete);
        return new ChunkHeatmapModel.Plan(quads, hover, base.icons(), base.weakEdgeMasks());
    }

    private static Map<Long, WeakChunkSnapshot.ChunkEntry> indexChunks(
            List<WeakChunkSnapshot.ChunkEntry> weakChunks) {
        Map<Long, WeakChunkSnapshot.ChunkEntry> result = new HashMap<>();
        for (WeakChunkSnapshot.ChunkEntry entry : weakChunks) {
            result.put(ChunkPos.asLong(entry.chunkX(), entry.chunkZ()), entry);
        }
        return result;
    }

    private static void addUnrepresented(Map<Long, WeakChunkSnapshot.ChunkEntry> weakByChunk,
                                         List<ChunkHeatmapModel.Quad> quads,
                                         Map<Long, ChunkHeatmapModel.HoverInfo> hover,
                                         boolean incomplete) {
        weakByChunk.forEach((key, weak) -> {
            hover.put(key, new ChunkHeatmapModel.HoverInfo(null, List.of(), List.of(),
                    weak.entityCount(), weak.entityTypeCounts(), false, List.of(), 0, incomplete));
            quads.add(new ChunkHeatmapModel.Quad(weak.chunkX(), weak.chunkZ(),
                    ChunkHeatmapModel.combinedColor(null, weak.entityCount())));
        });
    }

    private static ChunkHeatmapModel.HoverInfo withWeak(
            ChunkHeatmapModel.HoverInfo base, WeakChunkSnapshot.ChunkEntry weak) {
        if (base == null) {
            return new ChunkHeatmapModel.HoverInfo(null, List.of(), List.of(), weak.entityCount(),
                    weak.entityTypeCounts(), false, List.of(), 0, false);
        }
        return new ChunkHeatmapModel.HoverInfo(base.metric(), base.topEntities(), base.topBlockEntities(),
                weak.entityCount(), weak.entityTypeCounts(), base.possibleWeak(), base.loaderSources(),
                base.omittedLoaderSources(), base.incomplete());
    }
}
