package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WarashiNetwork {
    static final String NETWORK_VERSION = "7";

    private WarashiNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .optional()
                .playToClient(PerfReportPayload.TYPE, PerfReportPayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handlePerfReport(payload))
                .playToClient(WeakChunkPayload.TYPE, WeakChunkPayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handleWeakChunks(payload))
                .playToClient(WeakChunkCorrectionPayload.TYPE,
                        WeakChunkCorrectionPayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handleWeakChunkCorrection(payload))
                .playToClient(LoaderControlStatePayload.TYPE, LoaderControlStatePayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handleLoaderControl(payload))
                .playToClient(PerfSpatialSnapshotPayload.TYPE,
                        PerfSpatialSnapshotPayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handlePerfSpatial(payload))
                .playToClient(PerfWorldHotspotPayload.TYPE,
                        PerfWorldHotspotPayload.STREAM_CODEC,
                        (payload, context) -> ClientPayloadHandlers.handlePerfWorld(payload))
                .playToServer(PerfMapSubscriptionPayload.TYPE, PerfMapSubscriptionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                handleSubscription(player, payload);
                            }
                        })
                .playToServer(PerfScreenSubscriptionPayload.TYPE,
                        PerfScreenSubscriptionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                handleScreenSubscription(player, payload);
                            }
                        })
                .playToServer(PerfControlPayload.TYPE, PerfControlPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                handlePerfControl(player, payload.action());
                            }
                        })
                .playToServer(WeakChunkClearPayload.TYPE, WeakChunkClearPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                WeakChunkClearJobs.submit(player, new WeakChunkClearJobs.Request(
                                        payload.dimension(), payload.chunkX(), payload.chunkZ(), payload.target()));
                            }
                        })
                .playToServer(LoaderFreezeActionPayload.TYPE, LoaderFreezeActionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                LoaderFreezeDelivery.handle(player, payload);
                            }
                        });
    }

    private static void handleSubscription(
            ServerPlayer player, PerfMapSubscriptionPayload payload) {
        TicketPerfRuntime.DeliverySnapshot snapshot = TicketPerfRuntime.currentDeliverySnapshot();
        PerfReportDelivery.setSubscriptions(player, payload,
                snapshot.report(), snapshot.hotspots(),
                TicketPerfRuntime.isProfiling());
        WeakChunkDelivery.setMapSubscribed(player.getUUID(), payload.worldHotspots());
    }

    private static void handleScreenSubscription(
            ServerPlayer player, PerfScreenSubscriptionPayload payload) {
        if (!payload.open()) {
            PerfReportDelivery.closeScreen(player);
        }
    }

    private static void handlePerfControl(ServerPlayer player, PerfControlPayload.Action action) {
        if (!player.hasPermissions(2) || player.getServer() == null) {
            return;
        }
        switch (action) {
            case TOGGLE -> toggleProfiling(player);
            case CLEAR -> clearReport(player);
        }
    }

    private static void toggleProfiling(ServerPlayer player) {
        if (TicketPerfRuntime.isProfiling()) {
            TicketPerfRuntime.stopCurrentAnalysis(player.getServer());
            publishCurrent(player, false);
            return;
        }
        var result = TicketPerfRuntime.start(
                player.getServer(), TicketPerfConfig.autoStopSeconds(), player.getUUID());
        if (result.started() && player.getServer().isPaused()) {
            publishCurrent(player, true);
        }
    }

    private static void clearReport(ServerPlayer player) {
        if (TicketPerfRuntime.clearLastReport()) {
            publishCurrent(player, false);
        }
    }

    private static void publishCurrent(ServerPlayer player, boolean profiling) {
        TicketPerfRuntime.DeliverySnapshot snapshot = TicketPerfRuntime.currentDeliverySnapshot();
        PerfReportDelivery.publishNow(
                player.getServer(), snapshot.report(), snapshot.hotspots(), profiling);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static boolean canSendPerfReport(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, PerfReportPayload.TYPE.id());
    }

    public static void sendPerfReport(ServerPlayer player, PerfReportData report) {
        sendPerfReport(player, report, PerfReportPayload.Mode.DISPLAY, false);
    }

    public static void sendPerfReport(ServerPlayer player,
                                      PerfReportData report,
                                      PerfReportPayload.Mode mode,
                                      boolean profiling) {
        PacketDistributor.sendToPlayer(player, new PerfReportPayload(report, mode, profiling));
    }

    public static void sendPerfMapProjection(
            ServerPlayer player, PerfReportData report, boolean profiling) {
        PacketDistributor.sendToPlayer(player,
                PerfSpatialSnapshotPayload.forMap(report, profiling));
    }

    public static void sendPerfWorldProjection(
            ServerPlayer player, PerfReportData report, boolean profiling) {
        PacketDistributor.sendToPlayer(player,
                PerfSpatialSnapshotPayload.forWorld(report, profiling));
    }

    public static void sendPerfWorldHotspots(
            ServerPlayer player, PerfWorldHotspotPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendWeakChunks(ServerPlayer player, WeakChunkSnapshot snapshot,
                                      long sweepId, boolean sweepComplete) {
        PacketDistributor.sendToPlayer(
                player, new WeakChunkPayload(snapshot, sweepId, sweepComplete));
    }

    @SuppressWarnings("UnstableApiUsage")
    public static boolean canSendWeakChunks(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, WeakChunkPayload.TYPE.id());
    }

    @SuppressWarnings("UnstableApiUsage")
    public static boolean canSendWeakChunkCorrection(ServerPlayer player) {
        return NetworkRegistry.hasChannel(
                player.connection, WeakChunkCorrectionPayload.TYPE.id());
    }

    public static void sendWeakChunkCorrection(
            ServerPlayer player, WeakChunkCorrectionPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendLoaderControlState(
            ServerPlayer player, LoaderControlStatePayload state) {
        PacketDistributor.sendToPlayer(player, state);
    }

}
