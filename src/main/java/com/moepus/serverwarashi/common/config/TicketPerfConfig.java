package com.moepus.serverwarashi.common.config;

/**
 * performance 模块配置门面。
 */
public final class TicketPerfConfig {
    public static final int DEFAULT_AUTO_STOP_SECONDS = 15 * 60;
    public static final int MAX_ANALYZE_SECONDS = 86_400;

    private TicketPerfConfig() {
    }

    public static int autoStopSeconds() {
        return ServerConfig.PERF_AUTO_STOP_SECONDS.get();
    }

    public static int sampleIntervalMicros() {
        return ServerConfig.PERF_SAMPLE_INTERVAL_US.get();
    }
}
