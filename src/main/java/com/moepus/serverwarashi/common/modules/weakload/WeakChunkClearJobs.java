package com.moepus.serverwarashi.common.modules.weakload;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class WeakChunkClearJobs {
    public static final int ENTITIES_PER_TICK = 1_000;
    private static final int REMOVAL_BATCH_SIZE = 128;
    private static final int VALIDATION_TOP_TYPES = 3;
    private static final int ATTEMPT_PASSES = 2;
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static final Set<JobKey> ACTIVE = new HashSet<>();

    private WeakChunkClearJobs() {
    }

    public static boolean submit(ServerPlayer player, Request request) {
        if (!player.hasPermissions(2)
                || !request.dimension().equals(player.serverLevel().dimension().location().toString())) {
            return false;
        }
        long chunkPos = ChunkPos.asLong(request.chunkX(), request.chunkZ());
        WeakChunkSnapshot.ChunkEntry warning = validatedTarget(
                player.serverLevel(), chunkPos, request.target());
        if (warning == null) {
            return false;
        }
        Job job = new Job(new JobKey(player.serverLevel().dimension(), chunkPos),
                request.target(), attemptBudget(warning.entityCount()));
        if (!ACTIVE.add(job.key())) {
            return false;
        }
        JOBS.addLast(job);
        return true;
    }

    public static List<CompletedChunk> tick(MinecraftServer server) {
        List<CompletedChunk> completed = new ArrayList<>();
        int remaining = ENTITIES_PER_TICK;
        int jobsToVisit = JOBS.size();
        while (remaining > 0 && jobsToVisit-- > 0) {
            Job job = JOBS.removeFirst();
            BatchResult result;
            try {
                result = process(server, job, remaining);
            } catch (Throwable failure) {
                ACTIVE.remove(job.key());
                throw propagate(failure);
            }
            remaining -= result.attempted();
            job.consumeAttempts(result.attempted());
            if (result.complete() || job.attemptLimitReached()) {
                ACTIVE.remove(job.key());
                completed.add(new CompletedChunk(job.key().dimension(), job.key().chunkPos()));
            } else {
                JOBS.addLast(job);
            }
        }
        return List.copyOf(completed);
    }

    public static void clear() {
        JOBS.clear();
        ACTIVE.clear();
    }

    private static WeakChunkSnapshot.ChunkEntry validatedTarget(ServerLevel level,
                                                                long chunkPos,
                                                                WeakEntityType target) {
        WeakChunkSnapshot.ChunkEntry warning = WeakChunkCollector.inspect(level, chunkPos);
        return isEstimatedTarget(warning, target) ? warning : null;
    }

    static boolean isEstimatedTarget(WeakChunkSnapshot.ChunkEntry warning, WeakEntityType target) {
        return warning != null
                && warning.entityCount() >= WeakChunkSnapshot.ENTITY_RED_THRESHOLD
                && warning.topTypes(VALIDATION_TOP_TYPES).stream()
                .anyMatch(entry -> entry.getKey().equals(target));
    }

    static long attemptBudget(int initialEntityCount) {
        return (long) initialEntityCount * ATTEMPT_PASSES + ENTITIES_PER_TICK;
    }

    private static BatchResult process(MinecraftServer server, Job job, int tickLimit) {
        ServerLevel level = server.getLevel(job.key().dimension());
        if (level == null) {
            return new BatchResult(0, true);
        }
        EntitySectionStorage<Entity> storage = WeakChunkEntityAccess.storage(level);
        int limit = job.limit(tickLimit);
        int attempted = 0;
        while (attempted < limit) {
            job.ensureSections(storage);
            if (job.atEnd()) {
                if (job.closeCycle(storage)) {
                    return new BatchResult(attempted, true);
                }
                continue;
            }
            attempted += processCurrentSection(job, storage, limit - attempted);
        }
        return new BatchResult(attempted, false);
    }

    private static int processCurrentSection(Job job,
                                             EntitySectionStorage<Entity> storage,
                                             int limit) {
        long sectionId = job.currentSectionId();
        EntitySection<Entity> section = storage.getSection(sectionId);
        if (section == null || section.getStatus() != Visibility.TRACKED) {
            job.cursor().finishSection();
            return 0;
        }
        SelectionSource source = new SelectionSource(section, job.target(), job.cursor());
        Selection selection = select(source, Math.min(REMOVAL_BATCH_SIZE, limit));
        WeakChunkBatchRemoval.Result result = selection.targets().isEmpty()
                ? WeakChunkBatchRemoval.Result.EMPTY
                : WeakChunkBatchRemoval.clear(
                new WeakChunkBatchRemoval.SectionRef(storage, sectionId, section), selection.targets());
        job.cursor().recordRemovals(result.removed());
        advanceCursor(job.cursor(), selection, currentEntities(storage, sectionId));
        return result.attempted();
    }

    private static Selection select(SelectionSource source, int limit) {
        // This selector is for removing a chunk's validated top entity types. Do not reuse it for
        // sparse matches: non-matching entities are scanned without consuming the removal budget.
        List<Entity> entities = WeakChunkEntityAccess.entities(source.section());
        WeakChunkClearCursor.Window window = source.cursor().window(entities);
        int index = window.start();
        int end = index + window.length();
        List<WeakChunkBatchRemoval.Target> result = new ArrayList<>(limit);
        Set<Entity> selected = identitySet();
        while (index < end && result.size() < limit) {
            Entity entity = entities.get(index++);
            WeakChunkBatchRemoval.Target candidate = target(entity, source.section(), source.target());
            if (candidate != null && selected.add(entity)) {
                result.add(candidate);
            }
        }
        Entity nextAnchor = index == end ? null : entities.get(index);
        return new Selection(result, index - window.start(), nextAnchor);
    }

    private static WeakChunkBatchRemoval.Target target(Entity entity,
                                                       EntitySection<Entity> section,
                                                       WeakEntityType target) {
        if (entity.isRemoved() || !WeakEntityClassifier.matches(target, entity)) {
            return null;
        }
        return WeakChunkBatchRemoval.target(entity, section);
    }

    private static void advanceCursor(WeakChunkClearCursor cursor,
                                      Selection selection,
                                      List<Entity> entities) {
        if (selection.sectionComplete()) {
            cursor.finishSection();
            return;
        }
        cursor.advance(selection.examined(), selection.nextAnchor(), entities);
    }

    private static Set<Entity> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static List<Entity> currentEntities(EntitySectionStorage<Entity> storage, long sectionId) {
        EntitySection<Entity> current = storage.getSection(sectionId);
        return current == null ? List.of() : WeakChunkEntityAccess.entities(current);
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Weak entity removal failed", failure);
    }

    public record Request(String dimension, int chunkX, int chunkZ, WeakEntityType target) {
    }

    public record CompletedChunk(ResourceKey<Level> dimension, long chunkPos) {
    }

    private record JobKey(ResourceKey<Level> dimension, long chunkPos) {
    }

    private record SelectionSource(EntitySection<Entity> section,
                                   WeakEntityType target,
                                   WeakChunkClearCursor cursor) {
    }

    private record Selection(List<WeakChunkBatchRemoval.Target> targets,
                             int examined,
                             Entity nextAnchor) {
        private boolean sectionComplete() {
            return nextAnchor == null;
        }
    }

    private record BatchResult(int attempted, boolean complete) {
    }

    private static final class Job {
        private final JobKey key;
        private final WeakEntityType target;
        private final WeakChunkClearCursor cursor = new WeakChunkClearCursor();
        private long attemptsRemaining;
        private long[] sectionIds;

        private Job(JobKey key, WeakEntityType target, long attemptsRemaining) {
            this.key = key;
            this.target = target;
            this.attemptsRemaining = attemptsRemaining;
        }

        private void ensureSections(EntitySectionStorage<Entity> storage) {
            if (sectionIds == null) {
                sectionIds = loadSections(storage);
            }
        }

        private boolean atEnd() {
            return cursor.sectionIndex() >= sectionIds.length;
        }

        private boolean closeCycle(EntitySectionStorage<Entity> storage) {
            if (cursor.completeCycle()) {
                return true;
            }
            sectionIds = loadSections(storage);
            return false;
        }

        private long currentSectionId() {
            return sectionIds[cursor.sectionIndex()];
        }

        private long[] loadSections(EntitySectionStorage<Entity> storage) {
            return storage.getExistingSectionPositionsInChunk(key.chunkPos()).toArray();
        }

        private int limit(int requested) {
            return (int) Math.min(requested, attemptsRemaining);
        }

        private void consumeAttempts(int attempted) {
            attemptsRemaining -= attempted;
        }

        private boolean attemptLimitReached() {
            return attemptsRemaining == 0;
        }

        private JobKey key() {
            return key;
        }

        private WeakEntityType target() {
            return target;
        }

        private WeakChunkClearCursor cursor() {
            return cursor;
        }
    }

}
