package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportTextRenderer;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
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
    private static final PerfViewerSubscriptions VIEWERS = new PerfViewerSubscriptions();
    private static final LiveUpdateSender LIVE_UPDATE_SENDER = new LiveUpdateSender();
    private static final PerfViewerUpdateDispatcher<ServerPlayer> LIVE_UPDATE_DISPATCHER =
            new PerfViewerUpdateDispatcher<>(VIEWERS, LIVE_UPDATE_SENDER);
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
            sendWorldHotspots(player,
                    TicketPerfRuntime.currentDeliverySnapshot().hotspots(), true);
            return;
        }
        source.sendSuccess(() -> PerfReportTextRenderer.render(report), false);
    }

    public static void toPlayerOrConsole(MinecraftServer server, UUID playerId, PerfReportData report) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null && WarashiNetwork.canSendPerfReport(player)) {
            ONE_SHOT_UPDATES.add(player.getUUID());
            sendToPlayer(player, prepare(report, false), false);
            sendWorldHotspots(player,
                    TicketPerfRuntime.currentDeliverySnapshot().hotspots(), true);
            return;
        }
        Component message = PerfReportTextRenderer.render(report);
        if (player == null) {
            server.sendSystemMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }

    public static boolean open(ServerPlayer player,
                               PerfReportData report,
                               PerfWorldHotspotIndex hotspots,
                               boolean profiling) {
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
        sendWorldHotspots(player, hotspots, true);
        return true;
    }

    public static void setSubscriptions(ServerPlayer player,
                                        PerfMapSubscriptionPayload subscription,
                                        PerfReportData report,
                                        PerfWorldHotspotIndex hotspots,
                                        boolean profiling) {
        UUID playerId = player.getUUID();
        if (!subscription.subscribed()) {
            VIEWERS.setMap(playerId, false);
            VIEWERS.setWorld(playerId, false);
            removeUnusedWorldCursor(playerId);
            return;
        }
        if (!canSubscribeMap(WarashiNetwork.canSendPerfReport(player),
                PerfSubscriptionAccess.isPermitted(player))) {
            VIEWERS.setMap(playerId, false);
            VIEWERS.setWorld(playerId, false);
            removeUnusedWorldCursor(playerId);
            return;
        }
        boolean newWorldViewer = subscription.worldHotspots() && !VIEWERS.isWorldViewer(playerId);
        VIEWERS.setMap(playerId, subscription.mapOpen());
        VIEWERS.setWorld(playerId, subscription.worldHotspots());
        if (!subscription.worldHotspots()) {
            removeUnusedWorldCursor(playerId);
        }
        boolean paused = player.getServer() != null && player.getServer().isPaused();
        if (subscription.mapOpen() && !shouldDeferInitialReport(report, profiling, paused)) {
            WarashiNetwork.sendPerfMapProjection(player, prepare(report, profiling), profiling);
        } else if (subscription.worldHotspots()
                && !shouldDeferInitialReport(report, profiling, paused)) {
            WarashiNetwork.sendPerfWorldProjection(player, prepare(report, profiling), profiling);
        }
        if (newWorldViewer && !shouldDeferInitialReport(report, profiling, paused)) {
            sendWorldHotspots(player, hotspots, true);
        }
    }

    public static void closeScreen(ServerPlayer player) {
        UUID playerId = player.getUUID();
        VIEWERS.closeScreen(playerId);
        removeUnusedWorldCursor(playerId);
    }

    public static void tick(MinecraftServer server,
                            PerfReportData report,
                            PerfWorldHotspotIndex hotspots,
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
        pushToViewers(server, prepare(report, true), hotspots, true);
    }

    public static void publishNow(MinecraftServer server,
                                  PerfReportData report,
                                  PerfWorldHotspotIndex hotspots,
                                  boolean profiling) {
        resetPushClock();
        pushToViewers(server, prepare(report, profiling), hotspots, profiling);
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

    private static void pushToViewers(MinecraftServer server,
                                      PerfReportData report,
                                      PerfWorldHotspotIndex hotspots,
                                      boolean profiling) {
        if (report == null) {
            return;
        }
        var update = new PerfViewerUpdateDispatcher.Update(report, hotspots, profiling);
        for (UUID viewer : VIEWERS.recipients()) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || !canReceiveLiveUpdate(player, viewer)) {
                continue;
            }
            LIVE_UPDATE_DISPATCHER.dispatch(viewer, player, update);
        }
    }

    public static void onLogout(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
        PerfWorldHotspotDelivery.remove(player.getUUID());
    }

    public static void clearRuntimeState() {
        VIEWERS.clear();
        ENTITY_SCANS.clear();
        ONE_SHOT_UPDATES.clear();
        PerfWorldHotspotDelivery.clear();
        latestProfiling = false;
        resetPushClock();
    }

    static Iterable<UUID> mapViewers() {
        return VIEWERS.mapViewers();
    }

    static Iterable<UUID> worldViewers() {
        return VIEWERS.worldViewers();
    }

    static boolean isMapViewer(UUID playerId) {
        return VIEWERS.isMapViewer(playerId);
    }

    static boolean isWorldViewer(UUID playerId) {
        return VIEWERS.isWorldViewer(playerId);
    }

    static boolean hasMapRecipients() {
        return VIEWERS.hasMapRecipients();
    }

    private static void resetPushClock() {
        lastPushedTickCount = -1L;
    }

    private static void removeUnusedWorldCursor(UUID playerId) {
        if (!VIEWERS.receivesWorldHotspots(playerId)) {
            PerfWorldHotspotDelivery.remove(playerId);
        }
    }

    private static void sendToPlayer(ServerPlayer player, PerfReportData report, boolean profiling) {
        PerfReportPayload.Mode mode = VIEWERS.isLiveViewer(player.getUUID())
                ? PerfReportPayload.Mode.UPDATE
                : PerfReportPayload.Mode.DISPLAY;
        WarashiNetwork.sendPerfReport(player, report, mode, profiling);
    }

    private static void sendWorldHotspots(ServerPlayer player,
                                          PerfWorldHotspotIndex hotspots,
                                          boolean forceFull) {
        UUID playerId = player.getUUID();
        PerfWorldHotspotDelivery.send(player, hotspots, forceFull,
                VIEWERS.receivesWorldHotspots(playerId));
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
            if (player == null) {
                continue;
            }
            boolean supportsChannel = WarashiNetwork.canSendPerfReport(player);
            boolean liveViewer = retainPermittedLiveViewer(player, viewer, supportsChannel);
            if (!supportsChannel) {
                continue;
            }
            if (!liveViewer && !ONE_SHOT_UPDATES.contains(viewer)) {
                continue;
            }
            if (VIEWERS.isScreenViewer(viewer) || ONE_SHOT_UPDATES.contains(viewer)) {
                PerfReportPayload.Mode mode = VIEWERS.isScreenViewer(viewer)
                        ? VIEWERS.nextMode(viewer) : PerfReportPayload.Mode.UPDATE;
                WarashiNetwork.sendPerfReport(player, report, mode, latestProfiling);
            }
            if (VIEWERS.isMapViewer(viewer)) {
                WarashiNetwork.sendPerfMapProjection(player, report, latestProfiling);
            } else if (VIEWERS.receivesWorldProjection(viewer)
                    && !ONE_SHOT_UPDATES.contains(viewer)) {
                WarashiNetwork.sendPerfWorldProjection(player, report, latestProfiling);
            }
        }
        ONE_SHOT_UPDATES.clear();
    }

    private static boolean canReceiveLiveUpdate(ServerPlayer player, UUID playerId) {
        boolean supportsChannel = WarashiNetwork.canSendPerfReport(player);
        boolean retained = retainPermittedLiveViewer(
                player, playerId, supportsChannel);
        return supportsChannel && retained;
    }

    private static boolean retainPermittedLiveViewer(
            ServerPlayer player, UUID playerId, boolean supportsChannel) {
        boolean retained = VIEWERS.retainPermittedLiveViewer(playerId,
                supportsChannel && PerfSubscriptionAccess.isPermitted(player));
        if (!retained) {
            removeUnusedWorldCursor(playerId);
        }
        return retained;
    }

    static boolean canSubscribeMap(boolean supportsChannel, boolean hasPermission) {
        return supportsChannel && hasPermission;
    }

    static boolean shouldDeferInitialReport(PerfReportData report,
                                            boolean profiling,
                                            boolean serverPaused) {
        return profiling && !serverPaused && report != null && report.tickCount() == 0L;
    }

    private static final class LiveUpdateSender
            implements PerfViewerUpdateDispatcher.Sender<ServerPlayer> {
        @Override
        public void report(ServerPlayer player, PerfReportData report,
                           PerfReportPayload.Mode mode, boolean profiling) {
            WarashiNetwork.sendPerfReport(player, report, mode, profiling);
        }

        @Override
        public void spatial(ServerPlayer player, PerfReportData report,
                            PerfViewerUpdateDispatcher.SpatialTarget target,
                            boolean profiling) {
            if (target == PerfViewerUpdateDispatcher.SpatialTarget.MAP) {
                WarashiNetwork.sendPerfMapProjection(player, report, profiling);
            } else {
                WarashiNetwork.sendPerfWorldProjection(player, report, profiling);
            }
        }

        @Override
        public void hotspots(ServerPlayer player, PerfWorldHotspotIndex hotspots) {
            sendWorldHotspots(player, hotspots, false);
        }
    }

}
