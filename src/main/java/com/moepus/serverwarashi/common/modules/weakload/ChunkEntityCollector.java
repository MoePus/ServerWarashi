package com.moepus.serverwarashi.common.modules.weakload;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ChunkEntityCollector {
    private ChunkEntityCollector() {
    }

    public static ScanResult scanWeak(ServerLevel level, ScanCursor cursor, int chunkBudget) {
        if (chunkBudget <= 0) {
            throw new IllegalArgumentException("Chunk budget must be positive");
        }
        String dimension = level.dimension().location().toString();
        EntitySectionStorage<Entity> storage = WeakChunkEntityAccess.storage(level);
        LongSortedSet sectionIds = WeakChunkEntityAccess.sectionIds(storage);
        if (sectionIds.isEmpty()) {
            return cursor.emptyResult();
        }
        cursor.prepare(dimension, sectionIds);
        List<WeakChunkSnapshot.ChunkEntry> warnings = new ArrayList<>();
        LongIterator sections = cursor.iterator(sectionIds);
        long pendingSection = 0L;
        boolean hasPendingSection = false;
        int scanned = 0;
        while (scanned < chunkBudget) {
            if (!hasPendingSection && !sections.hasNext()) {
                if (!cursor.wrap()) {
                    return cursor.complete(warnings, scanned);
                }
                sections = cursor.iterator(sectionIds);
                continue;
            }
            long sectionId = hasPendingSection ? pendingSection : sections.nextLong();
            hasPendingSection = false;
            long chunkPos = ChunkPos.asLong(SectionPos.x(sectionId), SectionPos.z(sectionId));
            if (cursor.skipRepeatedStartChunk(chunkPos)) {
                cursor.advance(sections, chunkPos);
                if (cursor.hasPendingSection()) {
                    pendingSection = cursor.pendingSection();
                    hasPendingSection = true;
                }
                continue;
            }
            WeakChunkSnapshot.ChunkEntry entry = collectTrackedChunk(storage, dimension, chunkPos);
            if (entry != null) {
                warnings.add(entry);
            }
            scanned++;
            cursor.advance(sections, chunkPos);
            if (cursor.hasPendingSection()) {
                pendingSection = cursor.pendingSection();
                hasPendingSection = true;
            }
        }
        return new ScanResult(List.copyOf(warnings), cursor.sweepId(), false, scanned, false);
    }

    public static WeakChunkSnapshot.ChunkEntry inspectTracked(ServerLevel level, long chunkPos) {
        return collectTrackedChunk(WeakChunkEntityAccess.storage(level),
                level.dimension().location().toString(), chunkPos);
    }

    static WeakChunkSnapshot.ChunkEntry inspectAccessible(ServerLevel level, long chunkPos) {
        return inspect(level, chunkPos, Visibility::isAccessible).toWeakEntry();
    }

    public static ChunkEntitySummary inspectTicking(ServerLevel level, long chunkPos) {
        return inspect(level, chunkPos, Visibility::isTicking);
    }

    public static VisibilitySummaries inspectByVisibility(ServerLevel level, long chunkPos) {
        EntitySectionStorage<Entity> storage = WeakChunkEntityAccess.storage(level);
        String dimension = level.dimension().location().toString();
        List<EntitySection<Entity>> tracked = new ArrayList<>();
        List<EntitySection<Entity>> ticking = new ArrayList<>();
        storage.getExistingSectionsInChunk(chunkPos).forEach(section -> {
            if (section.getStatus() == Visibility.TRACKED) {
                tracked.add(section);
            } else if (section.getStatus().isTicking()) {
                ticking.add(section);
            }
        });
        return new VisibilitySummaries(
                summarize(tracked, dimension, chunkPos),
                summarize(ticking, dimension, chunkPos));
    }

    private static WeakChunkSnapshot.ChunkEntry collectTrackedChunk(
            EntitySectionStorage<Entity> storage, String dimension, long chunkPos) {
        List<EntitySection<Entity>> sections = sections(
                storage, chunkPos, visibility -> visibility == Visibility.TRACKED);
        int entityCount = entityCount(sections);
        if (entityCount < WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) {
            return null;
        }
        return summarize(sections, dimension, chunkPos, entityCount).toWeakEntry();
    }

    private static ChunkEntitySummary inspect(ServerLevel level,
                                              long chunkPos,
                                              Predicate<Visibility> visibility) {
        EntitySectionStorage<Entity> storage = WeakChunkEntityAccess.storage(level);
        return summarize(sections(storage, chunkPos, visibility),
                level.dimension().location().toString(), chunkPos);
    }

    private static ChunkEntitySummary summarize(List<EntitySection<Entity>> sections,
                                                String dimension,
                                                long chunkPos) {
        return summarize(sections, dimension, chunkPos, entityCount(sections));
    }

    private static ChunkEntitySummary summarize(List<EntitySection<Entity>> sections,
                                                String dimension,
                                                long chunkPos,
                                                int entityCount) {
        ChunkPos position = new ChunkPos(chunkPos);
        if (entityCount == 0) {
            return new ChunkEntitySummary(position.x, position.z, 0, Map.of());
        }
        Map<WeakEntityType, TypeEstimate> typeCounts = sampleTypes(
                sections, dimension, chunkPos, entityCount);
        return new ChunkEntitySummary(position.x, position.z, entityCount, typeCounts);
    }

    private static int entityCount(List<EntitySection<Entity>> sections) {
        return sections.stream().mapToInt(EntitySection::size).sum();
    }

    private static List<EntitySection<Entity>> sections(
            EntitySectionStorage<Entity> storage,
            long chunkPos,
            Predicate<Visibility> visibility) {
        return storage.getExistingSectionsInChunk(chunkPos)
                .filter(section -> visibility.test(section.getStatus()))
                .toList();
    }

    private static Map<WeakEntityType, TypeEstimate> sampleTypes(
            List<EntitySection<Entity>> sections,
            String dimension,
            long chunkPos,
            int entityCount) {
        int[] indices = StableEntitySampler.sampleIndices(entityCount, StableEntitySampler.SAMPLE_LIMIT,
                StableEntitySampler.seed(dimension, chunkPos, entityCount));
        Map<WeakEntityType, MutableTypeSample> samples = new HashMap<>();
        List<List<Entity>> entitySections = sections.stream()
                .map(WeakChunkEntityAccess::entities)
                .toList();
        for (Entity entity : StableEntitySampler.sampleValues(entitySections, indices)) {
            samples.computeIfAbsent(WeakEntityClassifier.typeOf(entity),
                    ignored -> new MutableTypeSample(entity)).count++;
        }
        Map<WeakEntityType, Integer> sampleCounts = new HashMap<>();
        samples.forEach((type, sample) -> sampleCounts.put(type, sample.count));
        Map<WeakEntityType, Integer> estimates = StableEntitySampler.estimateCounts(
                sampleCounts, indices.length, entityCount);
        Map<WeakEntityType, TypeEstimate> result = new HashMap<>();
        estimates.forEach((type, count) -> {
            MutableTypeSample sample = samples.get(type);
            result.put(type, new TypeEstimate(count, sample.x, sample.y, sample.z));
        });
        return Map.copyOf(result);
    }

    public record ChunkEntitySummary(int chunkX,
                                     int chunkZ,
                                     int entityCount,
                                     Map<WeakEntityType, TypeEstimate> typeEstimates) {
        public ChunkEntitySummary {
            if (entityCount < 0) {
                throw new IllegalArgumentException("Entity count must be non-negative");
            }
            typeEstimates = Map.copyOf(typeEstimates);
            long estimatedTotal = typeEstimates.values().stream()
                    .mapToLong(TypeEstimate::count)
                    .sum();
            if (estimatedTotal != entityCount) {
                throw new IllegalArgumentException("Type estimates must sum to the entity count");
            }
        }

        public List<Map.Entry<WeakEntityType, TypeEstimate>> topTypes(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Top type limit must be non-negative");
            }
            return typeEstimates.entrySet().stream()
                    .sorted(Map.Entry.<WeakEntityType, TypeEstimate>comparingByValue(
                                    java.util.Comparator.comparingInt(TypeEstimate::count))
                            .reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(limit)
                    .toList();
        }

        public WeakChunkSnapshot.ChunkEntry toWeakEntry() {
            if (entityCount < WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) {
                return null;
            }
            Map<WeakEntityType, Integer> counts = new HashMap<>();
            typeEstimates.forEach((type, estimate) -> counts.put(type, estimate.count()));
            return new WeakChunkSnapshot.ChunkEntry(chunkX, chunkZ, entityCount, counts);
        }
    }

    public record TypeEstimate(int count, int x, int y, int z) {
        public TypeEstimate {
            if (count <= 0) {
                throw new IllegalArgumentException("Estimated type count must be positive");
            }
        }
    }

    public record VisibilitySummaries(ChunkEntitySummary tracked,
                                      ChunkEntitySummary ticking) {
    }

    private static final class MutableTypeSample {
        private int count;
        private final int x;
        private final int y;
        private final int z;

        private MutableTypeSample(Entity entity) {
            x = entity.getBlockX();
            y = entity.getBlockY();
            z = entity.getBlockZ();
        }
    }

    public static final class ScanCursor {
        private long sweepId;
        private long sweepStart;
        private long nextSectionId;
        private long startChunk;
        private boolean initialized;
        private boolean wrapped;
        private boolean exhausted;
        private boolean skipStartChunk;
        private boolean emptySweepSent;

        void prepare(String dimension, LongSortedSet sectionIds) {
            if (emptySweepSent) {
                emptySweepSent = false;
            }
            if (initialized) {
                return;
            }
            sweepStart = sweepId == 0L
                    ? sectionIds.firstLong()
                    : StableEntitySampler.sweepStart(dimension, sweepId);
            LongSortedSet initialRegion = sectionIds.tailSet(sweepStart);
            if (initialRegion.isEmpty()) {
                sweepStart = sectionIds.firstLong();
                initialRegion = sectionIds;
            }
            startChunk = chunkPosition(initialRegion.firstLong());
            skipStartChunk = true;
            nextSectionId = sweepStart;
            wrapped = false;
            exhausted = false;
            initialized = true;
        }

        LongIterator iterator(LongSortedSet sectionIds) {
            if (exhausted) {
                return it.unimi.dsi.fastutil.longs.LongLists.EMPTY_LIST.iterator();
            }
            LongSortedSet region = wrapped
                    ? sectionIds.headSet(sweepStart).tailSet(nextSectionId)
                    : sectionIds.tailSet(nextSectionId);
            return region.iterator();
        }

        void advance(LongIterator sections, long chunkPos) {
            while (sections.hasNext()) {
                long next = sections.nextLong();
                long nextChunk = ChunkPos.asLong(SectionPos.x(next), SectionPos.z(next));
                if (nextChunk != chunkPos) {
                    nextSectionId = next;
                    return;
                }
            }
            nextSectionId = Long.MIN_VALUE;
            exhausted = true;
        }

        boolean hasPendingSection() {
            return !exhausted;
        }

        long pendingSection() {
            return nextSectionId;
        }

        boolean wrap() {
            if (wrapped) {
                return false;
            }
            wrapped = true;
            nextSectionId = Long.MIN_VALUE;
            exhausted = false;
            return true;
        }

        boolean skipRepeatedStartChunk(long chunkPos) {
            if (!wrapped || !skipStartChunk || chunkPos != startChunk) {
                return false;
            }
            skipStartChunk = false;
            return true;
        }

        ScanResult complete(List<WeakChunkSnapshot.ChunkEntry> warnings,
                            int scanned) {
            long completedSweep = sweepId;
            sweepId++;
            initialized = false;
            wrapped = false;
            exhausted = false;
            skipStartChunk = false;
            return new ScanResult(List.copyOf(warnings), completedSweep, true, scanned, false);
        }

        ScanResult emptyResult() {
            if (emptySweepSent) {
                return new ScanResult(List.of(), sweepId, false, 0, true);
            }
            emptySweepSent = true;
            long completedSweep = sweepId++;
            initialized = false;
            wrapped = false;
            exhausted = false;
            return new ScanResult(List.of(), completedSweep, true, 0, true);
        }

        public long sweepId() {
            return sweepId;
        }

        public void restart() {
            sweepId++;
            initialized = false;
            wrapped = false;
            exhausted = false;
            skipStartChunk = false;
            emptySweepSent = false;
        }

        private static long chunkPosition(long sectionId) {
            return ChunkPos.asLong(SectionPos.x(sectionId), SectionPos.z(sectionId));
        }
    }

    public record ScanResult(List<WeakChunkSnapshot.ChunkEntry> warnings,
                             long sweepId,
                             boolean sweepComplete,
                             int scannedChunks,
                             boolean emptyStorage) {
        public ScanResult {
            warnings = List.copyOf(warnings);
        }
    }
}
