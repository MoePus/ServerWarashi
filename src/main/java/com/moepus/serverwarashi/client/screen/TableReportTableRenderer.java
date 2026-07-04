package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public final class TableReportTableRenderer {
    public static final int ROW_H = 12;
    public static final int SECTION_GAP = 6;
    public static final int ACTION_GAP = 2;

    public static final int SECTION = 0xFF6FB6FF;
    public static final int COL_HEADER = 0xFFB8B8B8;
    public static final int ROW_TEXT = 0xFFE6E6E6;
    public static final int ROW_ALT_BG = 0x18FFFFFF;
    public static final int SEP = 0xFF333333;

    private static final String ELLIPSIS = "…";

    private TableReportTableRenderer() {
    }

    public static int renderSection(GuiGraphics graphics,
                                    Font font,
                                    TableReport.Section section,
                                    SectionRenderContext context,
                                    RowDecorator rowDecorator) {
        // 段标题（按可用宽截断）
        int tableMaxW = tableMaxWidth(context.width(), context.reservedRightWidth());
        graphics.drawString(font, fitToWidth(font, section.title(), tableMaxW), context.x(), context.y(), SECTION, true);
        int y = context.y() + ROW_H;
        // 无列头则只画标题
        if (section.columns().isEmpty()) {
            return y;
        }

        // 先计算各列宽度，再绘制表头，然后逐行绘制，每行绘制后调用 rowDecorator 补画行内按钮
        int[] widths = TableReportLayout.computeColumnWidths(section.columns(), section.rows(),
                font::width, tableMaxW);
        int tableW = Math.min(tableMaxW, TableReportLayout.sum(widths));
        renderHeader(graphics, font, section.columns(), widths, context.x(), y, tableW);
        y += ROW_H;

        for (int rowIndex = 0; rowIndex < section.rows().size(); rowIndex++) {
            renderRow(graphics, font, section, widths, context.x(), y, tableW, rowIndex);
            rowDecorator.render(rowIndex, y);
            y += ROW_H;
        }
        return y;
    }

    public static int contentHeight(TableReport report) {
        // 元信息高度
        int total = report.metadata().size() * ROW_H;
        if (!report.metadata().isEmpty()) {
            total += SECTION_GAP;
        }

        // 累加各非空段的高度 + 段间距
        for (TableReport.Section section : report.sections()) {
            if (section.rows().isEmpty()) {
                continue;
            }
            total += sectionHeight(section) + SECTION_GAP;
        }

        // 空报告/无可渲染段：留一行给 "No data"
        if (report.isEmpty()) {
            total += ROW_H;
        }
        return Math.max(ROW_H, total);
    }

    public static String fitToWidth(Font font, String text, int maxWidth) {
        // 空/零宽返回空串
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        // 放得下直接返回
        if (font.width(text) <= maxWidth) {
            return text;
        }
        // 放不下：截断并加省略号（宽度不够放省略号时直接硬截）
        int ellipsisWidth = font.width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) {
            return font.plainSubstrByWidth(text, maxWidth);
        }
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ELLIPSIS;
    }

    private static int sectionHeight(TableReport.Section section) {
        // 标题行 + (有列头则列头行) + 数据行
        int total = ROW_H;
        if (!section.columns().isEmpty()) {
            total += ROW_H;
        }
        return total + section.rows().size() * ROW_H;
    }

    private static int tableMaxWidth(int width, int reservedRightWidth) {
        // 扣除右侧动作区预留宽度后的可用表宽
        if (reservedRightWidth <= 0) {
            return Math.max(1, width);
        }
        return Math.max(1, width - reservedRightWidth - ACTION_GAP);
    }

    private static void renderHeader(GuiGraphics graphics,
                                     Font font,
                                     List<String> columns,
                                     int[] widths,
                                     int x,
                                     int y,
                                     int tableW) {
        // 逐列画表头文字（按列宽截断），底部画一条分隔线
        int columnX = x;
        for (int column = 0; column < widths.length; column++) {
            if (widths[column] > 0) {
                String text = fitToWidth(font, TableReportLayout.safeCell(columns, column),
                        Math.max(1, widths[column] - TableReportLayout.CELL_INSET));
                graphics.drawString(font, text, columnX + 2, y, COL_HEADER, true);
            }
            columnX += widths[column];
        }
        graphics.fill(x, y + ROW_H - 2, x + tableW, y + ROW_H - 1, SEP);
    }

    private static void renderRow(GuiGraphics graphics,
                                  Font font,
                                  TableReport.Section section,
                                  int[] widths,
                                  int x,
                                  int y,
                                  int tableW,
                                  int rowIndex) {
        // 偶数行加斑马底色
        if (rowIndex % 2 == 0) {
            graphics.fill(x, y - 1, x + tableW, y + ROW_H - 1, ROW_ALT_BG);
        }
        // 逐列画单元格
        int columnX = x;
        List<String> row = section.rows().get(rowIndex);
        for (int column = 0; column < widths.length; column++) {
            if (widths[column] > 0) {
                renderCell(graphics, font, section.columns(), row, columnX, y, widths[column], column);
            }
            columnX += widths[column];
        }
    }

    private static void renderCell(GuiGraphics graphics,
                                   Font font,
                                   List<String> columns,
                                   List<String> row,
                                   int x,
                                   int y,
                                   int width,
                                   int column) {
        // 单元格文字按列宽截断；数值列右对齐，其余左对齐
        String cell = fitToWidth(font, TableReportLayout.safeCell(row, column),
                Math.max(1, width - TableReportLayout.CELL_INSET));
        int textX = x + 2;
        if (TableReportLayout.isNumericColumn(TableReportLayout.safeCell(columns, column))) {
            textX = Math.max(x + 1, x + width - font.width(cell) - 2);
        }
        graphics.drawString(font, cell, textX, y, ROW_TEXT, true);
    }

    public record SectionRenderContext(int x, int y, int width, int reservedRightWidth) {
    }

    @FunctionalInterface
    public interface RowDecorator {
        void render(int rowIndex, int y);
    }
}
