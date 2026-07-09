package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 报告投递的唯一出口：决定走客户端 UI 包还是聊天栏文本。
 * 各功能模块只负责把数据构建成 {@link TableReport}/{@link TableReport.Result}，
 * 投递路径（在线？装了客户端？回退到哪里）在此统一裁决。
 */
public final class ReportDelivery {
    private ReportDelivery() {
    }

    /**
     * 命令来源：结构化报告优先（来源是装了客户端的玩家），否则回退为纯文本表格反馈。
     */
    public static void toCommandSource(CommandSourceStack source, Supplier<TableReport> report) {
        if (source.getEntity() instanceof ServerPlayer player && WarashiNetwork.canSendReports(player)) {
            WarashiNetwork.sendPerfReport(player, report.get());
            return;
        }
        source.sendSuccess(() -> TableReportRenderer.toComponent(report.get()), false);
    }

    /**
     * 后台到点：把报告发回触发玩家；玩家离线或无客户端则回退纯文本（玩家或控制台）。
     */
    public static void toPlayerOrConsole(MinecraftServer server, UUID playerId, TableReport report) {
        ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player != null && WarashiNetwork.canSendReports(player)) {
            WarashiNetwork.sendPerfReport(player, report);
            return;
        }
        Component message = TableReportRenderer.toComponent(report);
        if (player != null) {
            player.sendSystemMessage(message);
        } else {
            server.sendSystemMessage(message);
        }
    }
}
