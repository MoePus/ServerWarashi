package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

final class TableReportActions {
    static final int ACTION_H = 10;
    static final int ACTION_PAD = 6;

    private TableReportActions() {
    }

    static ActionHit renderButton(GuiGraphics graphics,
                                  Font font,
                                  List<ActionHit> hits,
                                  Button button,
                                  int x,
                                  int y,
                                  int mouseX,
                                  int mouseY,
                                  int minY,
                                  int maxY) {
        // 算宽度并按可视区裁剪；裁剪后判断鼠标是否悬停
        int width = buttonWidth(font, button.label());
        ButtonBounds clipped = clippedActionBounds(x, y, width, minY, maxY);
        boolean hover = clipped != null && contains(clipped, mouseX, mouseY);
        // 画底色（悬停高亮）+ 边框 + 居中文字
        graphics.fill(x, y, x + width, y + ACTION_H,
                hover ? TableReportPanelScreen.CLOSE_BG_HOVER : TableReportPanelScreen.CLOSE_BG);
        graphics.renderOutline(x, y, width, ACTION_H, TableReportPanelScreen.BORDER);
        int textX = x + (width - font.width(button.label())) / 2;
        graphics.drawString(font, button.label(), textX, y + 1, TableReportPanelScreen.ACTION_TEXT, true);
        // 完全被裁剪掉则不登记命中
        if (clipped == null) {
            return null;
        }
        // 登记命中区域，返回悬停命中（供 tooltip）
        ActionHit hit = new ActionHit(clipped, button.kind(), button.command(), button.section(), button.tooltip());
        hits.add(hit);
        return hover ? hit : null;
    }

    static int actionAreaWidth(Font font, TableReport.Section section) {
        // 右侧动作区宽度 = max(Copy 按钮宽, 各行按钮组宽)
        int width = buttonWidth(font, "Copy");
        for (int row = 0; row < section.rows().size(); row++) {
            width = Math.max(width, buttonsWidth(font, rowButtons(section, row)));
        }
        return width;
    }

    static int buttonsWidth(Font font, List<TableReport.Button> buttons) {
        // 一组按钮总宽（含间隔，末尾去掉一个间隔）
        if (buttons.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (TableReport.Button button : buttons) {
            width += buttonWidth(font, button.label()) + TableReportTableRenderer.ACTION_GAP;
        }
        return width - TableReportTableRenderer.ACTION_GAP;
    }

    static int buttonWidth(Font font, String label) {
        // 文字宽 + 左右内边距，且不小于 14
        return Math.max(14, font.width(label) + ACTION_PAD * 2);
    }

    static List<TableReport.Button> rowButtons(TableReport.Section section, int rowIndex) {
        if (section.rowButtons().isEmpty()) {
            return List.of();
        }
        return section.rowButtons().get(rowIndex);
    }

    static ActionHit hitAction(List<ActionHit> hits, double mouseX, double mouseY) {
        // 返回鼠标位置命中的那个 ActionHit；都未命中则返回 null
        for (ActionHit hit : hits) {
            if (contains(hit.bounds(), mouseX, mouseY)) {
                return hit;
            }
        }
        return null;
    }

    static String sectionClipboardText(TableReport.Section section) {
        // 复制用纯文本：列头 + 各行，用 | 连接、换行分隔
        List<String> lines = new ArrayList<>();
        if (!section.columns().isEmpty()) {
            lines.add(String.join(" | ", section.columns()));
        }
        for (List<String> row : section.rows()) {
            lines.add(String.join(" | ", row));
        }
        return String.join("\n", lines);
    }

    static String tooltipFor(String label) {
        // 按钮标签到 tooltip 的映射
        return switch (label) {
            case "TP" -> "Teleport";
            case "A" -> "Analyze";
            default -> label;
        };
    }

    private static ButtonBounds clippedActionBounds(int x, int y, int width, int minY, int maxY) {
        // 把按钮纵向裁剪到 [minY, maxY]；完全在区外返回 null
        int y0 = Math.max(y, minY);
        int y1 = Math.min(y + ACTION_H, maxY);
        if (y0 >= y1) {
            return null;
        }
        return new ButtonBounds(x, y0, x + width, y1);
    }

    private static boolean contains(ButtonBounds bounds, double mouseX, double mouseY) {
        // 点是否落在矩形内
        return mouseX >= bounds.x0() && mouseX < bounds.x1()
                && mouseY >= bounds.y0() && mouseY < bounds.y1();
    }

    record Button(String label,
                  ActionKind kind,
                  String command,
                  TableReport.Section section,
                  String tooltip) {
    }

    record ButtonBounds(int x0, int y0, int x1, int y1) {
    }

    record ActionHit(ButtonBounds bounds,
                     ActionKind kind,
                     String command,
                     TableReport.Section section,
                     String tooltip) {
    }

    enum ActionKind {
        COMMAND,
        COPY
    }
}
