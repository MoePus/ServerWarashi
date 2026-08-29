package com.moepus.serverwarashi.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务端配置定义（serverwarashi-server.toml）。
 */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.ConfigValue<Integer> PERF_AUTO_STOP_SECONDS = BUILDER
            .comment("Profiler auto-stop duration in seconds when no command duration is supplied.")
            .defineInRange("perf_auto_stop_seconds",
                    TicketPerfConfig.DEFAULT_AUTO_STOP_SECONDS,
                    1,
                    TicketPerfConfig.MAX_ANALYZE_SECONDS);
    static final ModConfigSpec.ConfigValue<Integer> PERF_SAMPLE_INTERVAL_US = BUILDER
            .comment("Profiler sampling interval in microseconds.")
            .defineInRange("perf_sample_interval_us", 1000, 100, 100_000);
    static final ModConfigSpec.ConfigValue<Integer> PERF_WORLD_HOTSPOT_LIMIT = BUILDER
            .comment("Maximum nearby entity and block-entity hotspots sent to each player.")
            .defineInRange("perf_world_hotspot_limit", 2048, 256, 4096);
    static final ModConfigSpec.BooleanValue PERF_ALLOW_NON_OP_SUBSCRIPTIONS = BUILDER
            .comment("Allow non-operator players to subscribe to performance reports.")
            .define("perf_allow_non_op_subscriptions", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }
}
