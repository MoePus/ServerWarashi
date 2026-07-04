package com.moepus.serverwarashi.client.screen;

import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

final class TableReportLayout {
    static final int CELL_PAD = 8;
    static final int CELL_INSET = 4;

    private static final Set<String> NUMERIC_COLUMNS = Set.of("chunks", "be", "e", "value");

    private TableReportLayout() {
    }

    static int[] computeColumnWidths(List<String> headers,
                                     List<List<String>> rows,
                                     ToIntFunction<String> widthOf,
                                     int maxTableW) {
        int columns = headers.size();
        int[] widths = new int[columns];
        if (columns == 0 || maxTableW <= 0) {
            return widths;
        }

        for (int column = 0; column < columns; column++) {
            widths[column] = Math.max(1, widthOf.applyAsInt(safeCell(headers, column)) + CELL_PAD);
        }
        for (List<String> row : rows) {
            for (int column = 0; column < columns; column++) {
                widths[column] = Math.max(widths[column], widthOf.applyAsInt(safeCell(row, column)) + CELL_PAD);
            }
        }

        int total = sum(widths);
        if (total <= maxTableW) {
            return widths;
        }

        int[] minWidths = new int[columns];
        for (int column = 0; column < columns; column++) {
            int headerWidth = widthOf.applyAsInt(safeCell(headers, column));
            if (isNumericColumn(safeCell(headers, column))) {
                minWidths[column] = Math.max(18, headerWidth + CELL_INSET);
            } else {
                minWidths[column] = Math.max(12, Math.min(widths[column], headerWidth + CELL_INSET));
            }
            minWidths[column] = Math.min(minWidths[column], widths[column]);
        }

        shrinkColumns(widths, minWidths, maxTableW, false, headers);
        shrinkColumns(widths, minWidths, maxTableW, true, headers);
        forceFitColumns(widths, maxTableW);
        return widths;
    }

    static int clampScroll(int scroll, int contentH, int viewH) {
        int maxScroll = Math.max(0, contentH - Math.max(0, viewH));
        if (scroll < 0) {
            return 0;
        }
        return Math.min(scroll, maxScroll);
    }

    static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    static boolean isNumericColumn(String header) {
        String normalized = header.trim().toLowerCase();
        return NUMERIC_COLUMNS.contains(normalized) || normalized.endsWith(" ms") || normalized.contains("mspt");
    }

    static String safeCell(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return "";
        }
        return cells.get(index);
    }

    private static void shrinkColumns(int[] widths,
                                      int[] minWidths,
                                      int maxTableW,
                                      boolean numeric,
                                      List<String> headers) {
        for (int column = 0; column < widths.length && sum(widths) > maxTableW; column++) {
            if (isNumericColumn(safeCell(headers, column)) != numeric) {
                continue;
            }
            int excess = sum(widths) - maxTableW;
            int shrink = Math.min(excess, widths[column] - minWidths[column]);
            if (shrink > 0) {
                widths[column] -= shrink;
            }
        }
    }

    private static void forceFitColumns(int[] widths, int maxTableW) {
        // 最后手段：前面按列收缩后仍超限时，逐列各减 1 px 循环收缩（上游保证 maxTableW > 0，每轮至少减 1px 必然终止）
        while (sum(widths) > maxTableW) {
            for (int column = 0; column < widths.length && sum(widths) > maxTableW; column++) {
                if (widths[column] > 0) {
                    widths[column]--;
                }
            }
        }
    }
}
