package com.moepus.serverwarashi.common.report;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

public final class TableReportChatRenderer {
    private TableReportChatRenderer() {
    }

    public static Component toComponent(TableReport report) {
        // 标题（青色）
        MutableComponent root = Component.literal(report.title() + "\n").withStyle(ChatFormatting.AQUA);
        // 元信息各行（灰色）
        for (String line : report.metadata()) {
            root = root.append(Component.literal(line + "\n").withStyle(ChatFormatting.GRAY));
        }
        for (TableReport.Section section : report.sections()) {
            // 段标题（深青）+ 列头（深灰，用 | 连接）
            root = root.append(Component.literal(section.title() + "\n").withStyle(ChatFormatting.DARK_AQUA));
            if (!section.columns().isEmpty()) {
                root = root.append(Component.literal(String.join(" | ", section.columns()) + "\n")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            List<List<String>> rows = section.rows();
            List<List<TableReport.Button>> rowButtons = section.rowButtons();
            for (int i = 0; i < rows.size(); i++) {
                // 数据行（灰色）
                MutableComponent line = Component.literal(String.join(" | ", rows.get(i)))
                        .withStyle(ChatFormatting.GRAY);
                // 该行若有行内按钮则逐个追加可点击按钮
                if (i < rowButtons.size()) {
                    for (TableReport.Button button : rowButtons.get(i)) {
                        line = line.append(Component.literal(" ")).append(clickable(button));
                    }
                }
                root = root.append(line).append(Component.literal("\n"));
            }
        }
        return root;
    }

    private static Component clickable(TableReport.Button button) {
        // 渲染为 [标签]：点击执行命令、悬浮显示命令文本
        String command = "/" + button.command();
        return Component.literal("[" + button.label() + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.LIGHT_PURPLE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
