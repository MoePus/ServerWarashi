package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.report.TableReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ClientResidentCostState {
    private static final long STALE_MILLIS = 5_000L;

    private static TableReport latestReport;
    private static long receivedAtMillis;

    private ClientResidentCostState() {
    }

    public static void update(TableReport report) {
        // 收到服务端推送：记录报告与到达时刻
        latestReport = report;
        receivedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        latestReport = null;
        receivedAtMillis = 0L;
    }

    public static Optional<TableReport> freshReport(int maxRows) {
        // 无报告或已过期（超过 STALE_MILLIS）则视为无数据
        if (latestReport == null || System.currentTimeMillis() - receivedAtMillis > STALE_MILLIS) {
            return Optional.empty();
        }
        // 逐段截断行数后重组报告
        List<TableReport.Section> sections = new ArrayList<>();
        for (TableReport.Section section : latestReport.sections()) {
            int rowCount = Math.min(maxRows, section.rows().size());
            List<List<String>> rows = section.rows().subList(0, rowCount);
            TableReport.Section trimmedSection;
            if (section.rowButtons().isEmpty()) {
                trimmedSection = TableReport.section(section.title(), section.columns(), rows, List.of());
            } else {
                trimmedSection = TableReport.section(section.title(), section.columns(), rows, section.rowButtons().subList(0, rowCount));
            }
            sections.add(trimmedSection);
        }
        return Optional.of(new TableReport(latestReport.title(), latestReport.metadata(), sections));
    }
}
