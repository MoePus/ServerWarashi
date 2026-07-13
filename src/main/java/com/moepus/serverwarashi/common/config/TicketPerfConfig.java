package com.moepus.serverwarashi.common.config;

/**
 * performance 模块配置门面。
 */
public final class TicketPerfConfig {
    public static final int DEFAULT_ANALYZE_SECONDS = 60;
    public static final int MAX_ANALYZE_SECONDS = 86_400;

    private TicketPerfConfig() {
    }

    public static int defaultAnalyzeSeconds() {
        return ServerConfig.PERF_DEFAULT_ANALYZE_SECONDS.get();
    }
}
