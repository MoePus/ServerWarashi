package com.moepus.serverwarashi.client.screen;

import java.util.List;

final class PerfReportPager {
    static final int PAGE_SIZE = 30;

    private PerfReportPager() {
    }

    static int pageCount(int rowCount) {
        return Math.max(1, (rowCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clampPage(int page, int rowCount) {
        return Math.max(0, Math.min(page, pageCount(rowCount) - 1));
    }

    static <T> List<T> page(List<T> rows, int page) {
        int first = clampPage(page, rows.size()) * PAGE_SIZE;
        int end = Math.min(rows.size(), first + PAGE_SIZE);
        return rows.subList(first, end);
    }
}
