package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportTextRenderer;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PerfReportDelivery {
    private static final ViewerSubscriptions VIEWERS = new ViewerSubscriptions();
    private static final PerfChunkEntityScanQueue ENTITY_SCANS = new PerfChunkEntityScanQueue();
    private static final Set<UUID> ONE_SHOT_UPDATES = new HashSet<>();
    private static long lastPushedTickCount = -1L;
    private static boolean latestProfiling;

    private PerfReportDelivery() {
    }

    public static void toCommandSource(CommandSourceStack source, PerfReportData report) {
        if (source.getEntity() instanceof ServerPlayer player && WarashiNetwork.canSendPerfReport(player)) {
            ONE_SHOT_UPDATES.add(player.getUUID());
            sendToPlayer(player, prepare(report, false), false);
            return;
        }
        source.sendSuccess(() -> PerfReportTextRenderer.render(report), false);
    }

    public static void toPlayerOrConsole(MinecraftServer server, UUID playerId, PerfReportData report) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null && WarashiNetwork.canSendPerfReport(player)) {
            ONE_SHOT_UPDATES.add(player.getUUID());
            sendToPlayer(player, prepare(report, false), false);
            return;
        }
        Component message = PerfReportTextRenderer.render(report);
        if (player == null) {
            server.sendSystemMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }

    public static boolean open(ServerPlayer player, PerfReportData report, boolean profiling) {
        if (!WarashiNetwork.canSendPerfReport(player)) {
            return false;
        }
        boolean deferred = shouldDeferInitialReport(report, profiling,
                player.getServer() != null && player.getServer().isPaused());
        VIEWERS.openScreen(player.getUUID(), deferred);
        if (deferred) {
            return true;
        }
        WarashiNetwork.sendPerfReport(
                player, prepare(report, profiling), PerfReportPayload.Mode.OPEN, profiling);
        return true;
    }

    public static void setMapSubscribed(ServerPlayer player,
                                        boolean subscribed,
                                        PerfReportData report,
                                        boolean profiling) {
        UUID playerId = player.getUUID();
        if (!subscribed) {
            VIEWERS.setMap(playerId, false);
            return;
        }
        if (!canSubscribeMap(WarashiNetwork.canSendPerfReport(player), player.hasPermissions(2))) {
            return;
        }
        VIEWERS.setMap(playerId, true);
        boolean paused = player.getServer() != null && player.getServer().isPaused();
        if (!shouldDeferInitialReport(report, profiling, paused)) {
            WarashiNetwork.sendPerfReport(
                    player, prepare(report, profiling), PerfReportPayload.Mode.UPDATE, profiling);
        }
    }

    public static void tick(MinecraftServer server,
                            PerfReportData report,
                            boolean profiling) {
        LoaderFreezeDelivery.publishQueued(server);
        if (VIEWERS.isEmpty() || report == null || !profiling) {
            resetPushClock();
            return;
        }
        if (!hasNewSnapshot(report, lastPushedTickCount)) {
            return;
        }
        lastPushedTickCount = report.tickCount();
        pushToViewers(server, prepare(report, true), true);
    }

    public static void publishNow(MinecraftServer server,
                                  PerfReportData report,
                                  boolean profiling) {
        resetPushClock();
        pushToViewers(server, prepare(report, profiling), profiling);
    }

    static PerfChunkEntityScanQueue.ScanBatch scanEntityCounts(
            MinecraftServer server, List<WeakChunkClearJobs.CompletedChunk> completed) {
        ENTITY_SCANS.prioritize(completed);
        PerfChunkEntityScanQueue.ScanBatch batch = ENTITY_SCANS.scan(server);
        if (batch.reportUpdate() != null) {
            pushEntityCorrection(server, batch.reportUpdate());
        } else {
            ONE_SHOT_UPDATES.clear();
        }
        return batch;
    }

    static boolean hasNewSnapshot(PerfReportData report, long previousTickCount) {
        return report != null && report.tickCount() > 0L && report.tickCount() != previousTickCount;
    }

    private static void pushToViewers(MinecraftServer server, PerfReportData report, boolean profiling) {
        if (report == null) {
            return;
        }
        for (UUID viewer : VIEWERS.recipients()) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !canReceiveLiveUpdate(player, viewer)) {
                continue;
            }
            WarashiNetwork.sendPerfReport(player, report, VIEWERS.nextMode(viewer), profiling);
        }
    }

    public static void onLogout(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
    }

    public static void clearRuntimeState() {
        VIEWERS.clear();
        ENTITY_SCANS.clear();
        ONE_SHOT_UPDATES.clear();
        latestProfiling = false;
        resetPushClock();
    }

    static Iterable<UUID> mapViewers() {
        return VIEWERS.mapViewers();
    }

    static boolean isMapViewer(UUID playerId) {
        return VIEWERS.isMapViewer(playerId);
    }

    static boolean hasMapRecipients() {
        return VIEWERS.hasMapRecipients();
    }

    private static void resetPushClock() {
        lastPushedTickCount = -1L;
    }

    private static void sendToPlayer(ServerPlayer player, PerfReportData report, boolean profiling) {
        PerfReportPayload.Mode mode = VIEWERS.isLiveViewer(player.getUUID())
                ? PerfReportPayload.Mode.UPDATE
                : PerfReportPayload.Mode.DISPLAY;
        WarashiNetwork.sendPerfReport(player, report, mode, profiling);
    }

    private static PerfReportData prepare(PerfReportData report, boolean profiling) {
        latestProfiling = profiling;
        return ENTITY_SCANS.accept(report);
    }

    private static void pushEntityCorrection(MinecraftServer server, PerfReportData report) {
        Set<UUID> recipients = VIEWERS.recipients();
        recipients.addAll(ONE_SHOT_UPDATES);
        for (UUID viewer : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !WarashiNetwork.canSendPerfReport(player)) {
                continue;
            }
            boolean liveViewer = VIEWERS.retainPermittedLiveViewer(
                    viewer, player.hasPermissions(2));
            if (!liveViewer && !ONE_SHOT_UPDATES.contains(viewer)) {
                continue;
            }
            PerfReportPayload.Mode mode = liveViewer
                    ? VIEWERS.nextMode(viewer) : PerfReportPayload.Mode.UPDATE;
            WarashiNetwork.sendPerfReport(player, report, mode, latestProfiling);
        }
        ONE_SHOT_UPDATES.clear();
    }

    private static boolean canReceiveLiveUpdate(ServerPlayer player, UUID playerId) {
        return WarashiNetwork.canSendPerfReport(player)
                && VIEWERS.retainPermittedLiveViewer(playerId, player.hasPermissions(2));
    }

    static boolean canSubscribeMap(boolean supportsChannel, boolean hasPermission) {
        return supportsChannel && hasPermission;
    }

    static boolean shouldDeferInitialReport(PerfReportData report,
                                            boolean profiling,
                                            boolean serverPaused) {
        return profiling && !serverPaused && report != null && report.tickCount() == 0L;
    }

    static final class ViewerSubscriptions {
        private final Set<UUID> screen = new HashSet<>();
        private final Set<UUID> map = new HashSet<>();
        private final Set<UUID> pendingScreenOpen = new HashSet<>();

        void openScreen(UUID playerId) {
            openScreen(playerId, false);
        }

        void openScreen(UUID playerId, boolean deferred) {
            screen.add(playerId);
            if (deferred) {
                pendingScreenOpen.add(playerId);
            } else {
                pendingScreenOpen.remove(playerId);
            }
        }

        void setMap(UUID playerId, boolean subscribed) {
            if (subscribed) {
                map.add(playerId);
            } else {
                map.remove(playerId);
            }
        }

        boolean isScreenViewer(UUID playerId) {
            return screen.contains(playerId);
        }

        boolean isMapViewer(UUID playerId) {
            return map.contains(playerId);
        }

        boolean isLiveViewer(UUID playerId) {
            return isScreenViewer(playerId) || isMapViewer(playerId);
        }

        boolean retainPermittedLiveViewer(UUID playerId, boolean hasMapPermission) {
            if (!hasMapPermission) {
                setMap(playerId, false);
            }
            return isLiveViewer(playerId);
        }

        PerfReportPayload.Mode nextMode(UUID playerId) {
            return pendingScreenOpen.remove(playerId)
                    ? PerfReportPayload.Mode.OPEN : PerfReportPayload.Mode.UPDATE;
        }

        Set<UUID> recipients() {
            Set<UUID> result = new HashSet<>(screen);
            result.addAll(map);
            return result;
        }

        Iterable<UUID> mapViewers() {
            return map;
        }

        boolean hasMapRecipients() {
            return !map.isEmpty();
        }

        boolean isEmpty() {
            return screen.isEmpty() && map.isEmpty();
        }

        void remove(UUID playerId) {
            screen.remove(playerId);
            map.remove(playerId);
            pendingScreenOpen.remove(playerId);
        }

        void clear() {
            screen.clear();
            map.clear();
            pendingScreenOpen.clear();
        }
    }
}
