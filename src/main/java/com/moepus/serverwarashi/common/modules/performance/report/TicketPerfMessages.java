package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 构建性能分析相关的提示与报告消息。
 */
public final class TicketPerfMessages {
    private TicketPerfMessages() {
    }

    public static Component noTicketOwnersFound() {
        return Component.literal("No ticket owners found.");
    }

    public static Component noActiveAnalysis() {
        return Component.literal("No active perf analysis.");
    }

    public static Component analysisAlreadyRunning() {
        return Component.literal("Another perf analysis is already running.");
    }

    public static Component noOwnerAtPosition(int x, int y, int z) {
        return Component.literal("No ticket owner at (" + x + ", " + y + ", " + z + ").");
    }

    public static Component multipleOwnersAtPosition(int x, int y, int z, int ownerCount) {
        return Component.literal("Multiple ticket owners at ("
                + x + ", " + y + ", " + z + "): " + ownerCount
                + ". Choose a chunk that belongs to only one owner, or run all-owner analysis.");
    }

    public static Component sessionStarted(ResourceKey<Level> dimension,
                                           Component ownerComponent,
                                           int chunkCount,
                                           int blockEntityCount,
                                           int entityCount,
                                           int durationSec) {
        MutableComponent message = Component.literal("Chunk perf analysis started (" + durationSec + "s) >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Dimension=" + dimension.location() + "\n").withStyle(ChatFormatting.GRAY));
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ownerComponent)
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
        message = message.append(Component.literal("    (chunks=" + chunkCount
                        + ", BE=" + blockEntityCount
                        + ", E=" + entityCount
                        + ").\n").withStyle(ChatFormatting.GRAY));
        return message;
    }

    public static Component allOwnersAnalysisStarted(ResourceKey<Level> dimension, int ownerCount, int durationSec) {
        MutableComponent message = Component.literal("All owner analysis started (" + durationSec + "s) >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Dimension=" + dimension.location()
                        + " | Owners=" + ownerCount + "\n").withStyle(ChatFormatting.GRAY));
        return message;
    }

    /**
     * 行内"传送(通用) + 分析(perf 特有)"按钮对；分析锚点为空则省略分析按钮。
     */
    public static List<TableReport.Button> locateButtons(ResourceLocation dimension,
                                                         BlockPos ownerPos,
                                                         BlockPos analyzeAnchor) {
        TableReport.Button analyze = analyzeAnchor == null ? null : new TableReport.Button("A", String.format(
                "execute in %s run warashi perf analyze start at %d %d %d %d",
                dimension, analyzeAnchor.getX(), analyzeAnchor.getY(), analyzeAnchor.getZ(),
                TicketPerfConfig.defaultAnalyzeSeconds()
        ));
        return ReportButtons.locate(dimension, ownerPos, analyze);
    }
}
