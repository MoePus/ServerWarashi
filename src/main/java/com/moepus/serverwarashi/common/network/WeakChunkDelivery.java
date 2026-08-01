package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkCollector;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WeakChunkDelivery {
    static final int IDLE_SWEEP_COOLDOWN_TICKS = 200;
    private static final Map<ServerLevel, ScanState> SCANNERS = new LinkedHashMap<>();
    private static final Map<UUID, ServerLevel> VIEWER_LEVELS = new HashMap<>();
    private static final Set<UUID> INITIAL_SYNC_PENDING = new HashSet<>();

    private WeakChunkDelivery() {
    }

    public static void setMapSubscribed(UUID playerId, boolean subscribed) {
        if (subscribed) {
            INITIAL_SYNC_PENDING.add(playerId);
            return;
        }
        INITIAL_SYNC_PENDING.remove(playerId);
        VIEWER_LEVELS.remove(playerId);
    }

    public static void tick(MinecraftServer server) {
        List<WeakChunkClearJobs.CompletedChunk> completed = WeakChunkClearJobs.tick(server);
        updateLevels(server);
        updateViewers(server);
        publishInitialSnapshots();
        publishCorrections(server, completed);
        SCANNERS.forEach(WeakChunkDelivery::scanAndPublish);
    }

    public static void clearRuntimeState() {
        SCANNERS.clear();
        VIEWER_LEVELS.clear();
        INITIAL_SYNC_PENDING.clear();
    }

    private static void updateLevels(MinecraftServer server) {
        Set<ServerLevel> loaded = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            loaded.add(level);
            SCANNERS.computeIfAbsent(level, ignored -> new ScanState());
        }
        SCANNERS.keySet().removeIf(level -> !loaded.contains(level));
        VIEWER_LEVELS.entrySet().removeIf(entry -> !loaded.contains(entry.getValue()));
    }

    private static void updateViewers(MinecraftServer server) {
        SCANNERS.values().forEach(state -> state.viewers.clear());
        Set<UUID> currentViewers = new HashSet<>();
        for (UUID viewerId : PerfReportDelivery.mapViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewerId);
            if (!isEligible(player)) {
                continue;
            }
            ScanState state = SCANNERS.get(player.serverLevel());
            if (state == null) {
                continue;
            }
            currentViewers.add(viewerId);
            state.viewers.add(player);
            ServerLevel previous = VIEWER_LEVELS.put(viewerId, player.serverLevel());
            if (previous != player.serverLevel()) {
                INITIAL_SYNC_PENDING.add(viewerId);
            }
        }
        VIEWER_LEVELS.keySet().removeIf(id -> !currentViewers.contains(id));
        INITIAL_SYNC_PENDING.removeIf(id -> !currentViewers.contains(id));
    }

    private static boolean isEligible(ServerPlayer player) {
        return player != null && player.hasPermissions(2)
                && WarashiNetwork.canSendWeakChunks(player);
    }

    private static void publishInitialSnapshots() {
        for (Map.Entry<ServerLevel, ScanState> scanner : SCANNERS.entrySet()) {
            ScanState state = scanner.getValue();
            for (ServerPlayer player : state.viewers) {
                if (!INITIAL_SYNC_PENDING.remove(player.getUUID())) {
                    continue;
                }
                sendInitialSnapshot(player, scanner.getKey(), state);
            }
        }
    }

    private static void sendInitialSnapshot(ServerPlayer player,
                                            ServerLevel level,
                                            ScanState state) {
        String dimension = dimension(level);
        if (state.cache.completedSweepId() >= 0L) {
            sendEntries(List.of(player), dimension, state.cache.completedGeneratedAt(),
                    state.cache.completedSweepId(), true, state.cache.completedEntries());
        }
        sendEntries(List.of(player), dimension, System.currentTimeMillis(),
                state.cursor.sweepId(), false, state.cache.currentEntries());
    }

    private static void scanAndPublish(ServerLevel level, ScanState state) {
        if (!state.throttle.shouldScan(!state.viewers.isEmpty())) {
            return;
        }
        WeakChunkCollector.ScanResult result = WeakChunkCollector.scan(
                level, state.cursor, WeakChunkCollector.CHUNKS_PER_TICK);
        if (result.emptyStorage()) {
            state.cache.clearCurrent();
        }
        state.cache.mergeCurrent(result.warnings());
        if (!result.warnings().isEmpty() || result.sweepComplete()) {
            sendEntries(state.viewers, dimension(level), System.currentTimeMillis(),
                    result.sweepId(), result.sweepComplete(), result.warnings());
        }
        if (result.sweepComplete()) {
            state.cache.complete(result.sweepId(), System.currentTimeMillis());
            state.throttle.onSweepComplete(state.viewers.isEmpty());
        } else if (result.emptyStorage()) {
            state.throttle.onEmptyStorage(state.viewers.isEmpty());
        }
    }

    private static void publishCorrections(
            MinecraftServer server, List<WeakChunkClearJobs.CompletedChunk> completed) {
        for (WeakChunkClearJobs.CompletedChunk chunk : completed) {
            ServerLevel level = server.getLevel(chunk.dimension());
            ScanState state = level == null ? null : SCANNERS.get(level);
            if (state == null) {
                continue;
            }
            WeakChunkSnapshot.ChunkEntry entry = WeakChunkCollector.inspect(level, chunk.chunkPos());
            state.cache.correct(chunk.chunkPos(), entry);
            sendCorrection(level, state.viewers, chunk.chunkPos(), entry);
        }
    }

    private static void sendCorrection(ServerLevel level,
                                       List<ServerPlayer> viewers,
                                       long chunkPos,
                                       WeakChunkSnapshot.ChunkEntry entry) {
        ChunkPos position = new ChunkPos(chunkPos);
        var payload = new WeakChunkCorrectionPayload(dimension(level), position.x, position.z,
                System.currentTimeMillis(), entry);
        for (ServerPlayer player : viewers) {
            if (canSend(level, player) && WarashiNetwork.canSendWeakChunkCorrection(player)) {
                WarashiNetwork.sendWeakChunkCorrection(player, payload);
            }
        }
    }

    private static void sendEntries(List<ServerPlayer> viewers,
                                    String dimension,
                                    long generatedAt,
                                    long sweepId,
                                    boolean complete,
                                    Iterable<WeakChunkSnapshot.ChunkEntry> entries) {
        List<WeakChunkSnapshot.ChunkEntry> chunks = new ArrayList<>();
        entries.forEach(chunks::add);
        if (chunks.isEmpty()) {
            sendBatch(viewers, dimension, generatedAt, sweepId, complete, List.of());
            return;
        }
        for (int offset = 0; offset < chunks.size(); offset += WeakChunkSnapshot.MAX_CHUNKS) {
            int end = Math.min(offset + WeakChunkSnapshot.MAX_CHUNKS, chunks.size());
            sendBatch(viewers, dimension, generatedAt, sweepId,
                    complete && end == chunks.size(), chunks.subList(offset, end));
        }
    }

    private static void sendBatch(List<ServerPlayer> viewers,
                                  String dimension,
                                  long generatedAt,
                                  long sweepId,
                                  boolean complete,
                                  List<WeakChunkSnapshot.ChunkEntry> chunks) {
        WeakChunkSnapshot snapshot = new WeakChunkSnapshot(dimension, generatedAt, chunks);
        for (ServerPlayer player : viewers) {
            if (canSend(dimension, player)) {
                WarashiNetwork.sendWeakChunks(player, snapshot, sweepId, complete);
            }
        }
    }

    private static boolean canSend(ServerLevel level, ServerPlayer player) {
        return canSend(dimension(level), player);
    }

    private static boolean canSend(String dimension, ServerPlayer player) {
        return shouldSend(dimension, dimension(player.serverLevel()),
                PerfReportDelivery.isMapViewer(player.getUUID()), player.hasPermissions(2))
                && WarashiNetwork.canSendWeakChunks(player);
    }

    static boolean shouldSend(String snapshotDimension,
                              String playerDimension,
                              boolean subscribed,
                              boolean permitted) {
        return snapshotDimension.equals(playerDimension) && subscribed && permitted;
    }

    private static void applyCorrection(Map<Long, WeakChunkSnapshot.ChunkEntry> cache,
                                        long chunkPos,
                                        WeakChunkSnapshot.ChunkEntry entry) {
        if (entry == null) {
            cache.remove(chunkPos);
        } else {
            cache.put(chunkPos, entry);
        }
    }

    private static void merge(Map<Long, WeakChunkSnapshot.ChunkEntry> target,
                              List<WeakChunkSnapshot.ChunkEntry> entries) {
        for (WeakChunkSnapshot.ChunkEntry entry : entries) {
            target.put(ChunkPos.asLong(entry.chunkX(), entry.chunkZ()), entry);
        }
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static final class ScanState {
        private final WeakChunkCollector.ScanCursor cursor = new WeakChunkCollector.ScanCursor();
        private final SweepCache cache = new SweepCache();
        private final ScanThrottle throttle = new ScanThrottle();
        private final List<ServerPlayer> viewers = new ArrayList<>();
    }

    static final class ScanThrottle {
        private int cooldownTicks = IDLE_SWEEP_COOLDOWN_TICKS;

        boolean shouldScan(boolean hasViewers) {
            if (hasViewers) {
                cooldownTicks = 0;
                return true;
            }
            if (cooldownTicks == 0) {
                return true;
            }
            cooldownTicks--;
            return false;
        }

        void onSweepComplete(boolean idle) {
            if (idle) {
                cooldownTicks = IDLE_SWEEP_COOLDOWN_TICKS;
            }
        }

        void onEmptyStorage(boolean idle) {
            if (idle) {
                cooldownTicks = IDLE_SWEEP_COOLDOWN_TICKS;
            }
        }

        int cooldownTicks() {
            return cooldownTicks;
        }
    }

    static final class SweepCache {
        private final Map<Long, WeakChunkSnapshot.ChunkEntry> current = new LinkedHashMap<>();
        private Map<Long, WeakChunkSnapshot.ChunkEntry> completed = new LinkedHashMap<>();
        private long completedSweepId = -1L;
        private long completedGeneratedAt;

        void mergeCurrent(List<WeakChunkSnapshot.ChunkEntry> entries) {
            merge(current, entries);
        }

        void clearCurrent() {
            current.clear();
        }

        void complete(long sweepId, long generatedAt) {
            completed = new LinkedHashMap<>(current);
            completedSweepId = sweepId;
            completedGeneratedAt = generatedAt;
            current.clear();
        }

        void correct(long chunkPos, WeakChunkSnapshot.ChunkEntry entry) {
            applyCorrection(completed, chunkPos, entry);
            applyCorrection(current, chunkPos, entry);
        }

        List<WeakChunkSnapshot.ChunkEntry> currentEntries() {
            return List.copyOf(current.values());
        }

        List<WeakChunkSnapshot.ChunkEntry> completedEntries() {
            return List.copyOf(completed.values());
        }

        long completedSweepId() {
            return completedSweepId;
        }

        long completedGeneratedAt() {
            return completedGeneratedAt;
        }
    }
}
