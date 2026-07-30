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

public final class WeakChunkCollector {
    public static final int CHUNKS_PER_TICK = 64;

    private WeakChunkCollector() {
    }

    public static ScanResult scan(ServerLevel level, ScanCursor cursor, int chunkBudget) {
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
            WeakChunkSnapshot.ChunkEntry entry = collectChunk(storage, dimension, chunkPos);
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
        return new ScanResult(List.copyOf(warnings), cursor.sweepId(), false, scanned);
    }

    static WeakChunkSnapshot.ChunkEntry inspect(ServerLevel level, long chunkPos) {
        return collectChunk(WeakChunkEntityAccess.storage(level),
                level.dimension().location().toString(), chunkPos);
    }

    private static WeakChunkSnapshot.ChunkEntry collectChunk(EntitySectionStorage<Entity> storage,
                                                               String dimension,
                                                               long chunkPos) {
        int entityCount = trackedEntityCount(storage, chunkPos);
        if (entityCount < WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) {
            return null;
        }
        List<EntitySection<Entity>> sections = storage.getExistingSectionsInChunk(chunkPos)
                .filter(section -> section.getStatus() == Visibility.TRACKED)
                .toList();
        Map<WeakEntityType, Integer> typeCounts = sampleTypes(
                sections, dimension, chunkPos, entityCount);
        ChunkPos position = new ChunkPos(chunkPos);
        return new WeakChunkSnapshot.ChunkEntry(position.x, position.z, entityCount, typeCounts);
    }

    private static int trackedEntityCount(EntitySectionStorage<Entity> storage, long chunkPos) {
        return storage.getExistingSectionsInChunk(chunkPos)
                .filter(section -> section.getStatus() == Visibility.TRACKED)
                .mapToInt(EntitySection::size)
                .sum();
    }

    private static Map<WeakEntityType, Integer> sampleTypes(List<EntitySection<Entity>> sections,
                                                             String dimension,
                                                             long chunkPos,
                                                             int entityCount) {
        int[] indices = StableEntitySampler.sampleIndices(entityCount, StableEntitySampler.SAMPLE_LIMIT,
                StableEntitySampler.seed(dimension, chunkPos, entityCount));
        Map<WeakEntityType, Integer> samples = new HashMap<>();
        List<List<Entity>> entitySections = sections.stream()
                .map(WeakChunkEntityAccess::entities)
                .toList();
        for (Entity entity : StableEntitySampler.sampleValues(entitySections, indices)) {
            samples.merge(WeakEntityClassifier.typeOf(entity), 1, Integer::sum);
        }
        return StableEntitySampler.estimateCounts(samples, indices.length, entityCount);
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
            return new ScanResult(List.copyOf(warnings), completedSweep, true, scanned);
        }

        ScanResult emptyResult() {
            if (emptySweepSent) {
                return new ScanResult(List.of(), sweepId, false, 0);
            }
            emptySweepSent = true;
            long completedSweep = sweepId++;
            initialized = false;
            wrapped = false;
            exhausted = false;
            return new ScanResult(List.of(), completedSweep, true, 0);
        }

        long sweepId() {
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
                             int scannedChunks) {
        public ScanResult {
            warnings = List.copyOf(warnings);
        }
    }
}
