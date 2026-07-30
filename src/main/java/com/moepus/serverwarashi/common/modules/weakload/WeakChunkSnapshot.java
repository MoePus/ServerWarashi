package com.moepus.serverwarashi.common.modules.weakload;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public record WeakChunkSnapshot(String dimension,
                                long generatedAtEpochMillis,
                                List<ChunkEntry> chunks) {
    public static final int MAX_CHUNKS = 8;
    public static final int MAX_SAMPLED_TYPES = 16;
    public static final int ENTITY_WARNING_THRESHOLD = 50;
    public static final int ENTITY_DANGER_THRESHOLD = 500;

    public WeakChunkSnapshot {
        requireId(dimension, "dimension");
        if (generatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("Snapshot time must be non-negative");
        }
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Too many weak chunks: " + chunks.size());
        }
        chunks = List.copyOf(chunks);
    }

    public record ChunkEntry(int chunkX,
                             int chunkZ,
                             int entityCount,
                             Map<WeakEntityType, Integer> entityTypeCounts) {
        public ChunkEntry {
            if (entityCount < ENTITY_WARNING_THRESHOLD) {
                throw new IllegalArgumentException("Weak chunk entity count is below threshold");
            }
            if (entityTypeCounts.isEmpty() || entityTypeCounts.size() > MAX_SAMPLED_TYPES) {
                throw new IllegalArgumentException("Invalid sampled entity type count");
            }
            TreeMap<WeakEntityType, Integer> sorted = new TreeMap<>();
            long total = 0L;
            for (var entry : entityTypeCounts.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    throw new IllegalArgumentException("Entity type estimates must be positive");
                }
                if (sorted.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalArgumentException("Duplicate entity type: " + entry.getKey());
                }
                total += entry.getValue();
            }
            if (total != entityCount) {
                throw new IllegalArgumentException("Entity type estimates must sum to entity count");
            }
            entityTypeCounts = Map.copyOf(sorted);
        }

        public List<Entry<WeakEntityType, Integer>> topTypes(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Top type limit must be non-negative");
            }
            return entityTypeCounts.entrySet().stream()
                    .sorted(Entry.<WeakEntityType, Integer>comparingByValue().reversed()
                            .thenComparing(Entry.comparingByKey()))
                    .limit(limit)
                    .toList();
        }
    }

    private static void requireId(String value, String label) {
        if (value == null || ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }
}
