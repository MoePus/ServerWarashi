package com.moepus.serverwarashi;

import com.moepus.serverwarashi.common.command.WarashiCommands;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 模组统一事件订阅入口。
 * 只负责接收 NeoForge 事件并分发到各模块运行时入口。
 * 层级：入口层。
 * 上游：NeoForge 事件总线。下游：TicketPerfRuntime / WarashiCommands。
 */
@EventBusSubscriber(modid = ServerWarashi.MODID)

public final class ServerWarashiEvents {
    private ServerWarashiEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 命令注册事件触发时，装配整棵 /warashi 命令树
        WarashiCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        TicketPerfRuntime.onServerTickPre(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        // 推进性能分析会话计时：到点则自动停止并产出报告
        TicketPerfRuntime.AutoReport report = TicketPerfRuntime.onServerTickPost(event.getServer());
        if (report != null) {
            PerfReportDelivery.toPlayerOrConsole(event.getServer(), report.playerId(), report.data());
        }
        PerfReportDelivery.tick(event.getServer(), TicketPerfRuntime.currentReportOrNull(),
                TicketPerfRuntime.isProfiling(), report != null);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TicketPerfRuntime.clearRuntimeState();
        PerfReportDelivery.clearRuntimeState();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PerfReportDelivery.onLogout(player);
        }
    }
}
