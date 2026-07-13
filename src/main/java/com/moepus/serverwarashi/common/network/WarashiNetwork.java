package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.cost.ResidentCostHudRuntime;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = ServerWarashi.MODID, bus = EventBusSubscriber.Bus.MOD)

public final class WarashiNetwork {
    private static final String NETWORK_VERSION = "4";

    private WarashiNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 注册为可选通道：服务端和客户端都装了本 mod 才启用，一方没装也不报错
        event.registrar(NETWORK_VERSION)
                .optional()
                // 服务端→客户端：报告 payload，主线程上分发给处理器
                .playToClient(ReportPayload.TYPE, ReportPayload.STREAM_CODEC,
                        (payload, context) -> ClientReportDispatch.handleReport(payload.kind(), payload.report()))
                // 客户端→服务端：驻留成本订阅，主线程上更新订阅集合
                .playToServer(ResidentCostSubscribePayload.TYPE, ResidentCostSubscribePayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                ResidentCostHudRuntime.subscribe(player, payload.on());
                            }
                        });
    }

    @SuppressWarnings("UnstableApiUsage")
    public static boolean canSendReports(ServerPlayer player) {
        // 玩家客户端是否注册了报告通道（即装了本 mod 客户端）
        return NetworkRegistry.hasChannel(player.connection, ReportPayload.TYPE.id());
    }

    public static void sendPerfReport(ServerPlayer player, TableReport report) {
        sendReport(player, ReportPayload.ReportKind.PERF_REPORT, report);
    }

    public static void sendResidentCost(ServerPlayer player, TableReport report) {
        sendReport(player, ReportPayload.ReportKind.RESIDENT_COST, report);
    }

    private static void sendReport(ServerPlayer player, ReportPayload.ReportKind kind, TableReport report) {
        // 统一封包并发给指定玩家
        PacketDistributor.sendToPlayer(player, new ReportPayload(kind, report));
    }
}
