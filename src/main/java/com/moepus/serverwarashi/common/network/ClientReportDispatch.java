package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.report.TableReport;

import java.util.Objects;
import java.util.function.Consumer;

public final class ClientReportDispatch {
    private static Consumer<TableReport> perfReportHandler = ignored -> {
    };
    private static Consumer<TableReport> residentCostHandler = ignored -> {
    };

    private ClientReportDispatch() {
    }

    public static void setPerfReportHandler(Consumer<TableReport> handler) {
        // 由客户端 setup 注入性能报告处理器（非空）
        perfReportHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setResidentCostHandler(Consumer<TableReport> handler) {
        // 由客户端 setup 注入驻留成本处理器（非空）
        residentCostHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handleReport(ReportPayload.ReportKind kind, TableReport report) {
        // 按报告类型分发到对应处理器
        switch (kind) {
            case PERF_REPORT -> perfReportHandler.accept(report);
            case RESIDENT_COST -> residentCostHandler.accept(report);
        }
    }
}
