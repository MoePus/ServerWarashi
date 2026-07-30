package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.network.WeakChunkPayload;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ClientWeakChunkState {
    private static final TreeMap<Long, WeakChunkSnapshot.ChunkEntry> ENTRIES = new TreeMap<>();
    private static final Set<Long> SWEEP_SEEN = new TreeSet<>();
    private static String dimension;
    private static long generatedAt;
    private static long activeSweep = -1L;
    private static long revision;
    private static Snapshot cachedSnapshot;
    private static boolean renderDirty;

    private ClientWeakChunkState() {
    }

    public static void update(WeakChunkPayload payload) {
        WeakChunkSnapshot snapshot = payload.snapshot();
        boolean changed = false;
        boolean snapshotChanged = false;
        if (!snapshot.dimension().equals(dimension)) {
            changed = !ENTRIES.isEmpty();
            snapshotChanged = true;
            ENTRIES.clear();
            SWEEP_SEEN.clear();
            dimension = snapshot.dimension();
            generatedAt = 0L;
            activeSweep = -1L;
        }
        if (payload.sweepId() < activeSweep) {
            return;
        }
        if (payload.sweepId() != activeSweep) {
            activeSweep = payload.sweepId();
            SWEEP_SEEN.clear();
        }
        for (WeakChunkSnapshot.ChunkEntry chunk : snapshot.chunks()) {
            long key = ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ());
            WeakChunkSnapshot.ChunkEntry previous = ENTRIES.put(key, chunk);
            changed |= !chunk.equals(previous);
            SWEEP_SEEN.add(key);
        }
        generatedAt = Math.max(generatedAt, snapshot.generatedAtEpochMillis());
        if (payload.sweepComplete()) {
            changed |= ENTRIES.keySet().removeIf(key -> !SWEEP_SEEN.contains(key));
        }
        snapshotChanged |= changed;
        if (snapshotChanged) {
            cachedSnapshot = null;
        }
        if (changed) {
            renderDirty = true;
        }
    }

    public static void tick() {
        if (renderDirty) {
            renderDirty = false;
            revision++;
            cachedSnapshot = null;
        }
    }

    public static Snapshot snapshot() {
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }
        var chunks = new ArrayList<WeakChunkSnapshot.ChunkEntry>(ENTRIES.size());
        chunks.addAll(ENTRIES.values());
        cachedSnapshot = new Snapshot(dimension, generatedAt, chunks, revision);
        return cachedSnapshot;
    }

    public static WeakChunkSnapshot.ChunkEntry chunk(String requestedDimension,
                                                      int chunkX,
                                                      int chunkZ) {
        if (!java.util.Objects.equals(dimension, requestedDimension)) {
            return null;
        }
        return ENTRIES.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static void clear() {
        ENTRIES.clear();
        SWEEP_SEEN.clear();
        dimension = null;
        generatedAt = 0L;
        activeSweep = -1L;
        renderDirty = false;
        revision++;
        cachedSnapshot = null;
    }

    public static void clearIfDimensionMismatch(String currentDimension) {
        if (dimension != null && !dimension.equals(currentDimension)) {
            clear();
        }
    }

    public record Snapshot(String dimension,
                           long generatedAtEpochMillis,
                           java.util.List<WeakChunkSnapshot.ChunkEntry> chunks,
                           long revision) {
        public Snapshot {
            chunks = java.util.List.copyOf(chunks);
        }
    }
}
