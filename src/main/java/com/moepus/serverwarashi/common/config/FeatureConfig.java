package com.moepus.serverwarashi.common.config;

public final class FeatureConfig {
    private FeatureConfig() {
    }

    public static boolean weakChunkScanEnabled() {
        return ServerConfig.WEAK_CHUNK_SCAN_ENABLED.get();
    }

    public static boolean watchdogTicketReportEnabled() {
        return ServerConfig.WATCHDOG_TICKET_REPORT_ENABLED.get();
    }
}
