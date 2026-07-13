package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.TicketOwner;
import com.moepus.serverwarashi.common.report.ReportButtons;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
                                           TicketOwner owner,
                                           int chunkCount,
                                           int blockEntityCount,
                                           int entityCount,
                                           int durationSec) {
        MutableComponent message = Component.literal("Chunk perf analysis started (" + durationSec + "s) >\n")
                .withStyle(ChatFormatting.AQUA);
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Dimension=" + dimension.location() + "\n").withStyle(ChatFormatting.GRAY));
        message = message.append(Component.literal("    ").withStyle(ChatFormatting.DARK_GRAY))
                .append(ownerComponent(owner, dimension))
                .append(Component.literal("\n").withStyle(ChatFormatting.DARK_GRAY));
        message = message.append(Component.literal("    (chunks=" + chunkCount
                        + ", BE=" + blockEntityCount
                        + ", E=" + entityCount
                        + ").\n").withStyle(ChatFormatting.GRAY));
        return message;
    }

    /**
     * owner 的可点击展示组件：名字（Block 带 modId 悬浮提示）+ 金色可点击传送坐标。
     */
    static Component ownerComponent(TicketOwner owner, ResourceKey<Level> dimension) {
        BlockPos pos = owner.representativePos();
        // 无坐标：仅文本，不可点击
        if (pos == null) {
            return Component.literal(owner.name() + "@null");
        }

        DisplayName displayName = resolveDisplayName(owner.name());

        // 普通部分：名字 + @
        Component base = Component.literal(displayName.text() + "@");
        if (displayName.hoverName() != null) {
            // 方块带 modId 时附悬浮提示
            base = base.copy().setStyle(
                    Style.EMPTY.withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal(displayName.hoverName())
                    ))
            );
        }

        // 坐标部分：金色 + 可点击传送
        Component coord = Component.literal("(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")")
                .setStyle(
                        Style.EMPTY
                                .withColor(TextColor.fromRgb(0xFFD700))
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/" + ReportButtons.teleportCommand(dimension.location(), pos)
                                ))
                );

        return Component.empty().append(base).append(coord);
    }

    static DisplayName resolveDisplayName(String rawName) {
        // 形如 "Block{modid:blockid}" 时，拆出简短名并把 modId 作为悬浮提示
        if (rawName.startsWith("Block{") && rawName.endsWith("}")) {
            int start = "Block{".length();
            int colon = rawName.indexOf(':', start);
            if (colon > start) {
                int end = rawName.length() - 1;
                String modId = rawName.substring(start, colon);
                String blockId = rawName.substring(colon + 1, end);
                return new DisplayName("Block{" + blockId + "}", modId);
            }
        }
        return new DisplayName(rawName, null);
    }

    record DisplayName(String text, String hoverName) {
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
