package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkCollector;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WeakChunkDelivery {
    private static final Map<ServerLevel, ScanState> SCANNERS = new LinkedHashMap<>();
    private static boolean refreshRequested;

    private WeakChunkDelivery() {
    }

    public static void requestRefresh() {
        refreshRequested = true;
    }

    public static void tick(MinecraftServer server) {
        WeakChunkClearJobs.tick(server);
        if (!PerfReportDelivery.hasMapRecipients()) {
            clearRuntimeState();
            return;
        }
        if (refreshRequested) {
            SCANNERS.values().forEach(state -> state.cursor.restart());
            refreshRequested = false;
        }
        updateViewers(server);
        SCANNERS.forEach(WeakChunkDelivery::scanAndSend);
    }

    public static void clearRuntimeState() {
        SCANNERS.clear();
        refreshRequested = false;
    }

    private static void updateViewers(MinecraftServer server) {
        SCANNERS.values().forEach(state -> state.viewers.clear());
        for (UUID viewer : PerfReportDelivery.mapViewers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !player.hasPermissions(2)) {
                continue;
            }
            SCANNERS.computeIfAbsent(player.serverLevel(), ignored -> new ScanState())
                    .viewers.add(player);
        }
        SCANNERS.values().removeIf(state -> state.viewers.isEmpty());
    }

    private static void scanAndSend(ServerLevel level, ScanState state) {
        WeakChunkCollector.ScanResult result = WeakChunkCollector.scan(
                level, state.cursor, WeakChunkCollector.CHUNKS_PER_TICK);
        if (result.warnings().isEmpty() && !result.sweepComplete()) {
            return;
        }
        sendResult(level, state.viewers, result);
    }

    private static void sendResult(ServerLevel level,
                                   List<ServerPlayer> viewers,
                                   WeakChunkCollector.ScanResult result) {
        String dimension = level.dimension().location().toString();
        long generatedAt = System.currentTimeMillis();
        List<WeakChunkSnapshot.ChunkEntry> chunks = result.warnings();
        if (chunks.isEmpty()) {
            sendBatch(viewers, dimension, generatedAt, result.sweepId(),
                    result.sweepComplete(), List.of());
            return;
        }
        for (int offset = 0; offset < chunks.size(); offset += WeakChunkSnapshot.MAX_CHUNKS) {
            int end = Math.min(offset + WeakChunkSnapshot.MAX_CHUNKS, chunks.size());
            boolean complete = result.sweepComplete() && end == chunks.size();
            sendBatch(viewers, dimension, generatedAt, result.sweepId(), complete,
                    chunks.subList(offset, end));
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
            if (shouldSend(dimension, player.serverLevel().dimension().location().toString(),
                    PerfReportDelivery.isMapViewer(player.getUUID()), player.hasPermissions(2))
                    && WarashiNetwork.canSendPerfReport(player)) {
                WarashiNetwork.sendWeakChunks(player, snapshot, sweepId, complete);
            }
        }
    }

    static boolean shouldSend(String snapshotDimension,
                              String playerDimension,
                              boolean subscribed,
                              boolean permitted) {
        return snapshotDimension.equals(playerDimension) && subscribed && permitted;
    }

    private static final class ScanState {
        private final WeakChunkCollector.ScanCursor cursor = new WeakChunkCollector.ScanCursor();
        private final List<ServerPlayer> viewers = new ArrayList<>();
    }
}
