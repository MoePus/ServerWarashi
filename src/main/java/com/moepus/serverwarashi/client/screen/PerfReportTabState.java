package com.moepus.serverwarashi.client.screen;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class PerfReportTabState {
    private static final int MAX_STABLE_ROWS = 4_096;
    int scroll;
    int page;
    int nextOrder;
    boolean stale;
    PerfReportRows.SortKey sort = PerfReportRows.SortKey.MSPT;
    final Set<String> expanded = new HashSet<>();
    final LinkedHashMap<String, Integer> stableOrder = new LinkedHashMap<>();
    List<PerfReportRows.Row> rows;

    void rememberOrder() {
        rows.forEach(row -> stableOrder.computeIfAbsent(row.key(), ignored -> nextOrder++));
        while (stableOrder.size() > MAX_STABLE_ROWS) {
            stableOrder.remove(stableOrder.keySet().iterator().next());
        }
    }

    void resort(PerfReportRows.SortKey requested) {
        sort = requested;
        stale = false;
        stableOrder.clear();
        nextOrder = 0;
        rows = null;
        page = 0;
        scroll = 0;
    }

    void refresh() {
        stale |= !stableOrder.isEmpty();
        rows = null;
    }
}
