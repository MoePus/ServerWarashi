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
    static final String NETWORK_VERSION = "2";

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
                .playToServer(PerfMapSubscriptionPayload.TYPE, PerfMapSubscriptionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PerfReportDelivery.setMapSubscribed(player, payload.subscribed(),
                                        TicketPerfRuntime.currentReport(), TicketPerfRuntime.isProfiling());
                                if (payload.subscribed()) {
                                    WeakChunkDelivery.requestRefresh();
                                }
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
                        });
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
            PerfReportDelivery.publishNow(player.getServer(), TicketPerfRuntime.currentReport(), false);
        } else {
            var result = TicketPerfRuntime.start(
                    player.getServer(), TicketPerfConfig.autoStopSeconds(), player.getUUID());
            if (result.started() && player.getServer().isPaused()) {
                PerfReportDelivery.publishNow(player.getServer(), TicketPerfRuntime.currentReport(), true);
            }
        }
    }

    private static void clearReport(ServerPlayer player) {
        if (!TicketPerfRuntime.clearLastReport()) {
            return;
        }
        PerfReportDelivery.publishNow(player.getServer(), TicketPerfRuntime.currentReport(), false);
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

    public static void sendWeakChunks(ServerPlayer player,
                                      WeakChunkSnapshot snapshot,
                                      long sweepId,
                                      boolean sweepComplete) {
        PacketDistributor.sendToPlayer(player, new WeakChunkPayload(snapshot, sweepId, sweepComplete));
    }

}
