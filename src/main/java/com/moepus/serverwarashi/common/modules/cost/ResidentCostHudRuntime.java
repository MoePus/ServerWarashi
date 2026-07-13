package com.moepus.serverwarashi.common.modules.cost;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.config.ResidentCostConfig;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.network.WarashiNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ResidentCostHudRuntime {
    private static final Set<UUID> SUBSCRIBERS = new HashSet<>();
    private static int ticksUntilPush;

    private ResidentCostHudRuntime() {
    }

    public static void subscribe(ServerPlayer player, boolean on) {
        // 订阅/退订该玩家的驻留成本 HUD 推送
        if (on) {
            SUBSCRIBERS.add(player.getUUID());
        } else {
            SUBSCRIBERS.remove(player.getUUID());
        }
    }

    public static void onLogout(ServerPlayer player) {
        SUBSCRIBERS.remove(player.getUUID());
    }

    public static void clearRuntimeState() {
        SUBSCRIBERS.clear();
        ticksUntilPush = 0;
    }

    public static void tick(MinecraftServer server) {
        // 无订阅者：复位计时并返回
        if (SUBSCRIBERS.isEmpty()) {
            ticksUntilPush = 0;
            return;
        }
        // 未到推送时刻：倒计时后返回
        if (ticksUntilPush > 0) {
            ticksUntilPush--;
            return;
        }

        // 到点：重置周期并推送一轮
        ticksUntilPush = ResidentCostConfig.residentHudPushPeriod() - 1;
        pushReports(server);
    }

    private static void pushReports(MinecraftServer server) {
        // 按维度缓存已构建的报告，避免同维度重复计算
        Map<ServerLevel, Optional<TableReport>> reports = new HashMap<>();
        for (UUID subscriber : SUBSCRIBERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(subscriber);
            // 玩家离线或无权限/不支持网络则跳过
            if (player == null || !canPushTo(player)) {
                continue;
            }
            // 取（或现算）该玩家所在维度的报告
            Optional<TableReport> report = reports.computeIfAbsent(player.serverLevel(), ResidentCostHudRuntime::buildReport);
            if (report.isEmpty()) {
                continue;
            }
            WarashiNetwork.sendResidentCost(player, report.get());
        }
    }

    private static boolean canPushTo(ServerPlayer player) {
        // 需要 OP 权限且客户端支持报告通道
        return player.hasPermissions(2) && WarashiNetwork.canSendReports(player);
    }

    private static Optional<TableReport> buildReport(ServerLevel level) {
        // 成本未开或该维度正在性能分析时不出报告
        if (!ChunkCostSampler.enabled() || TicketPerfRuntime.hasActiveSessionIn(level)) {
            return Optional.empty();
        }
        // 一次遍历获取 ACTIVE_ONLY 与 ALL 两个视图，避免双全量扫描
        OwnerChunks.BothMaps both = OwnerChunks.collectBothMaps(level);
        // 锚点计数按 ALL 全集（含已暂停 owner）：分析按钮的 startSingle 就是按 ALL 判定多 owner；若按 ACTIVE_ONLY 计数，发出的按钮点击后会因多 owner 被拒绝
        return Optional.of(ResidentCostReport.build(
                level,
                both.activeOnly(),
                ResidentCostConfig.residentHudMaxOwners(),
                OwnerChunks.ownerCountByChunk(both.all().values())
        ));
    }
}
