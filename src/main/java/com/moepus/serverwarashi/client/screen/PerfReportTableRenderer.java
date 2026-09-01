package com.moepus.serverwarashi.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.moepus.serverwarashi.client.screen.PerfReportStyle.*;

final class PerfReportTableRenderer {
    private PerfReportTableRenderer() {
    }

    static void renderHeader(GuiGraphics graphics, Font font, TableContext context, HeaderState state) {
        PerfReportGeometry geometry = context.geometry;
        int y = geometry.rowsY() - HEADER_HEIGHT;
        graphics.fill(geometry.contentX(), y, geometry.contentRight(), geometry.rowsY() - 2, HEADER);
        renderSortHighlights(graphics, context, state);
        int labelWidth = labelWidth(context);
        drawCell(graphics, font, PerfDisplayText.translated(context.layout.headingKey),
                geometry.contentX(), y + 2, labelWidth, false, MUTED);
        int x = geometry.contentX() + labelWidth;
        drawCell(graphics, font, PerfDisplayText.translated("screen.serverwarashi.profiler.header.pos"),
                x, y + 2, geometry.positionWidth(), false, MUTED);
        x = renderExtraHeader(graphics, font, context, x + geometry.positionWidth(), y + 2, state);
        renderMetricHeader(graphics, font, geometry, x, y + 2, state);
    }

    static List<SortHeader> sortHeaders(TableContext context) {
        PerfReportGeometry geometry = context.geometry;
        int y = geometry.rowsY() - HEADER_HEIGHT;
        int x = geometry.contentX() + labelWidth(context) + geometry.positionWidth();
        List<SortHeader> headers = new ArrayList<>(5);
        if (context.layout == Layout.CHUNKS) {
            headers.add(header(PerfReportRows.SortKey.ENTITIES, x, y, geometry.countWidth()));
            x += geometry.countWidth();
            headers.add(header(PerfReportRows.SortKey.BLOCK_ENTITIES, x, y, geometry.countWidth()));
            x += geometry.countWidth();
        } else if (context.layout == Layout.TICKABLES) {
            x += geometry.typeWidth();
        } else {
            headers.add(header(PerfReportRows.SortKey.CHUNKS, x, y, geometry.countWidth()));
            x += geometry.countWidth();
            x += geometry.lastLoginWidth();
        }
        headers.add(header(PerfReportRows.SortKey.MSPT, x, y, geometry.msptWidth()));
        x += geometry.msptWidth();
        headers.add(header(PerfReportRows.SortKey.MAX, x, y, geometry.maxWidth()));
        x += geometry.maxWidth();
        headers.add(header(PerfReportRows.SortKey.SAMPLES, x, y, geometry.samplesWidth()));
        return List.copyOf(headers);
    }

    private static SortHeader header(PerfReportRows.SortKey sort, int x, int y, int width) {
        return new SortHeader(sort, new PerfReportBounds(x, y, width, HEADER_HEIGHT - 2));
    }

    private static void renderSortHighlights(GuiGraphics graphics, TableContext context, HeaderState state) {
        for (SortHeader header : sortHeaders(context)) {
            boolean hovered = header.bounds.contains(state.mouseX, state.mouseY);
            if (header.sort == state.selected || hovered) {
                PerfReportBounds bounds = header.bounds;
                graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                        header.sort == state.selected ? SELECTED : HOVER);
            }
        }
    }

    static void renderRow(GuiGraphics graphics, Font font, RowContext context) {
        PerfReportRow row = context.row;
        PerfReportGeometry geometry = context.table.geometry;
        int labelWidth = labelWidth(context.table);
        renderLabel(graphics, font, context, labelWidth);
        int x = geometry.contentX() + labelWidth;
        renderPosition(graphics, font, context, x);
        x = renderExtraCells(graphics, font, context, x + geometry.positionWidth());
        renderMetricCells(graphics, font, geometry, row, x, context.y + 3);
    }

    static PerfReportBounds coordinateBounds(Font font, RowContext context) {
        if (context.row.content().coordinate().isEmpty()) {
            return null;
        }
        PerfReportGeometry geometry = context.table.geometry;
        String coordinate = fit(font, context.row.content().coordinate(),
                Math.max(1, geometry.positionWidth() - 6));
        int x = geometry.contentX() + labelWidth(context.table) + 3;
        return new PerfReportBounds(x, context.y, font.width(coordinate), ROW_HEIGHT);
    }

    static PerfReportBounds labelBounds(Font font, RowContext context) {
        int labelWidth = labelWidth(context.table);
        int depth = context.row.presentation().depth();
        int x = context.table.geometry.contentX() + depth * INDENT;
        int width = Math.max(1, labelWidth - depth * INDENT);
        String label = fit(font, expansionMarker(context.row)
                + context.row.content().labelPrefix(), width);
        return new PerfReportBounds(x, context.y, font.width(label), ROW_HEIGHT);
    }

    private static void renderLabel(GuiGraphics graphics, Font font, RowContext context, int labelWidth) {
        int depth = context.row.presentation().depth();
        int x = context.table.geometry.contentX() + depth * INDENT;
        int width = labelWidth - depth * INDENT;
        graphics.drawString(font, fit(font, expansionMarker(context.row)
                        + context.row.content().labelPrefix(), width),
                x, context.y + 3, TEXT, true);
    }

    private static void renderPosition(GuiGraphics graphics, Font font, RowContext context, int x) {
        drawCell(graphics, font, context.row.content().coordinate(), x, context.y + 3,
                context.table.geometry.positionWidth(), false, LINK);
    }

    private static int renderExtraHeader(GuiGraphics graphics, Font font, TableContext context,
                                         int x, int y, HeaderState state) {
        if (context.layout == Layout.CHUNKS) {
            drawHeaderCell(graphics, font, "E", PerfReportRows.SortKey.ENTITIES,
                    x, y, context.geometry.countWidth(), state);
            x += context.geometry.countWidth();
            drawHeaderCell(graphics, font, "BE", PerfReportRows.SortKey.BLOCK_ENTITIES,
                    x, y, context.geometry.countWidth(), state);
            return x + context.geometry.countWidth();
        }
        if (context.layout == Layout.TICKABLES) {
            drawCell(graphics, font, PerfDisplayText.translated("screen.serverwarashi.profiler.header.type"),
                    x, y, context.geometry.typeWidth(), false, MUTED);
            return x + context.geometry.typeWidth();
        }
        if (context.layout == Layout.PLAYERS) {
            drawHeaderCell(graphics, font, "C", PerfReportRows.SortKey.CHUNKS,
                    x, y, context.geometry.countWidth(), state);
            x += context.geometry.countWidth();
            drawCell(graphics, font,
                    PerfDisplayText.translated("screen.serverwarashi.profiler.header.last_login"),
                    x, y, context.geometry.lastLoginWidth(), false, MUTED);
            return x + context.geometry.lastLoginWidth();
        }
        return x;
    }

    private static int renderExtraCells(GuiGraphics graphics, Font font, RowContext context, int x) {
        PerfReportGeometry geometry = context.table.geometry;
        PerfReportRow.AuxiliaryCells cells = context.row.auxiliary();
        if (context.table.layout == Layout.CHUNKS) {
            drawCell(graphics, font, Integer.toString(cells.entityCount()), x, context.y + 3,
                    geometry.countWidth(), true, TEXT);
            x += geometry.countWidth();
            drawCell(graphics, font, Integer.toString(cells.blockEntityCount()), x, context.y + 3,
                    geometry.countWidth(), true, TEXT);
            return x + geometry.countWidth();
        }
        if (context.table.layout == Layout.TICKABLES) {
            drawCell(graphics, font, cells.type(), x, context.y + 3,
                    geometry.typeWidth(), false, TEXT);
            return x + geometry.typeWidth();
        }
        if (context.table.layout == Layout.PLAYERS) {
            drawCell(graphics, font, Integer.toString(cells.chunkCount()), x, context.y + 3,
                    geometry.countWidth(), true, TEXT);
            x += geometry.countWidth();
            String value = cells.playerStatus()
                    .map(status -> PerfTimeText.player(status.online(), status.lastLoginEpochMillis(),
                            System.currentTimeMillis()).getString())
                    .orElse("");
            drawCell(graphics, font, value, x, context.y + 3, geometry.lastLoginWidth(), false, TEXT);
            return x + geometry.lastLoginWidth();
        }
        return x;
    }

    private static void renderMetricHeader(GuiGraphics graphics, Font font,
                                           PerfReportGeometry geometry, int x, int y, HeaderState state) {
        drawHeaderCell(graphics, font, "MSPT", PerfReportRows.SortKey.MSPT,
                x, y, geometry.msptWidth(), state);
        x += geometry.msptWidth();
        drawHeaderCell(graphics, font, "MAX", PerfReportRows.SortKey.MAX,
                x, y, geometry.maxWidth(), state);
        x += geometry.maxWidth();
        drawHeaderCell(graphics, font, PerfDisplayText.translated("screen.serverwarashi.profiler.header.samples"),
                PerfReportRows.SortKey.SAMPLES,
                x, y, geometry.samplesWidth(), state);
    }

    private static void drawHeaderCell(GuiGraphics graphics, Font font, String value,
                                       PerfReportRows.SortKey sort, int x, int y, int width, HeaderState state) {
        String fitted = fit(font, value, Math.max(1, width - 8));
        Component label = headerLabel(fitted, sort, state);
        graphics.drawString(font, label, x + width - font.width(label) - 4, y, MUTED, true);
    }

    static Component headerLabel(String value, PerfReportRows.SortKey sort, HeaderState state) {
        boolean selected = sort == state.selected;
        return Component.literal(value).withStyle(style -> style
                .withBold(selected)
                .withItalic(selected && state.stale));
    }

    private static void renderMetricCells(GuiGraphics graphics, Font font, PerfReportGeometry geometry,
                                          PerfReportRow row, int x, int y) {
        if (!row.showsMetric()) {
            return;
        }
        drawCell(graphics, font, PerfReportRows.metric(row.metric().mspt()), x, y,
                geometry.msptWidth(), true, TEXT);
        x += geometry.msptWidth();
        drawCell(graphics, font, PerfReportRows.metric(row.metric().max()), x, y,
                geometry.maxWidth(), true, TEXT);
        x += geometry.maxWidth();
        drawCell(graphics, font, String.format(Locale.ROOT, "%,d", row.metric().samples()), x, y,
                geometry.samplesWidth(), true, TEXT);
    }

    static int labelWidth(TableContext context) {
        int auxiliary = switch (context.layout) {
            case CHUNKS -> context.geometry.countWidth() * 2;
            case TICKABLES -> context.geometry.typeWidth();
            case PLAYERS -> context.geometry.countWidth() + context.geometry.lastLoginWidth();
        };
        return context.geometry.labelWidth(auxiliary);
    }

    private static String expansionMarker(PerfReportRow row) {
        return row.capabilities().expansion()
                .map(expansion -> expansion.expanded() ? "[-] " : "[+] ")
                .orElse("");
    }

    private static void drawCell(GuiGraphics graphics, Font font, String value,
                                 int x, int y, int width, boolean right, int color) {
        String fitted = fit(font, value, Math.max(1, width - 6));
        int textX = right ? x + width - font.width(fitted) - 4 : x + 3;
        graphics.drawString(font, fitted, textX, y, color, true);
    }

    enum Layout {
        CHUNKS("screen.serverwarashi.profiler.header.chunk"),
        TICKABLES("screen.serverwarashi.profiler.header.tickable"),
        PLAYERS("screen.serverwarashi.profiler.header.player_source");

        private final String headingKey;

        Layout(String headingKey) {
            this.headingKey = headingKey;
        }
    }

    record TableContext(PerfReportGeometry geometry, Layout layout) {
    }

    record RowContext(TableContext table, PerfReportRow row, int y) {
    }

    record SortHeader(PerfReportRows.SortKey sort, PerfReportBounds bounds) {
    }

    record HeaderState(PerfReportRows.SortKey selected, boolean stale, int mouseX, int mouseY) {
    }

}
