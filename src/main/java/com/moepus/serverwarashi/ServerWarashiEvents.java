package com.moepus.serverwarashi;

import com.moepus.serverwarashi.common.command.WarashiCommands;
import com.moepus.serverwarashi.common.modules.bucket.TicketBucketRuntime;
import com.moepus.serverwarashi.common.modules.cost.ResidentCostHudRuntime;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfSessionController;
import com.moepus.serverwarashi.common.modules.idlefreeze.IdleFreezeRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 模组统一事件订阅入口。
 * 只负责接收 NeoForge 事件并分发到各模块运行时入口。
 */
@EventBusSubscriber(modid = Serverwarashi.MODID)

public final class ServerWarashiEvents {
    private ServerWarashiEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 命令注册事件触发时，装配整棵 /warashi 命令树
        WarashiCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 重放已存盘的 IDLE 冻结，并调度一次延迟的初始扫描
        IdleFreezeRuntime.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        // 每 tick 推进自动分桶的全局时钟（cycle = tickAge / runEvery 的计算基准）
        TicketBucketRuntime.onServerTickPre();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        // 推进性能分析会话计时：到点则自动停止并产出报告
        TicketPerfSessionController.tickSessions(event.getServer());
        // 按推送周期把驻留成本 Top owner 下发给订阅的管理员 HUD
        ResidentCostHudRuntime.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        // 仅服务端维度参与：执行该维度的自动分桶主循环（降负载核心链路）
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            boolean hasActiveSession = TicketPerfSessionController.activeSessionIdIn(serverLevel)
                    != TicketPerfSessionController.NO_SESSION_ID;
            TicketBucketRuntime.onLevelTickPre(serverLevel, hasActiveSession);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        // 仅服务端玩家参与：记录其所在 chunk 的访问日期，并尝试解冻该 chunk 所属冻结 owner
        if (event.getEntity() instanceof ServerPlayer player) {
            IdleFreezeRuntime.onPlayerTickPost(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // 服务器停止：清空三个模块的运行时状态，避免跨存档残留
        TicketBucketRuntime.clearRuntimeState();
        TicketPerfSessionController.clearRuntimeState();
        ResidentCostHudRuntime.clearRuntimeState();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家登出：从驻留成本 HUD 订阅列表中移除该玩家
        if (event.getEntity() instanceof ServerPlayer player) {
            ResidentCostHudRuntime.onLogout(player);
        }
    }
}
