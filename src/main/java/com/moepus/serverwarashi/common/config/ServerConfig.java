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
    static final ModConfigSpec.BooleanValue ENTITY_LOAD_GUARD_ENABLED = BUILDER
            .comment("Enable automatic protection against exceptionally large entity chunk loads.")
            .define("entity_load_guard_enabled", true);
    static final ModConfigSpec.ConfigValue<Integer> ENTITY_LOAD_GUARD_BATCH_THRESHOLD = BUILDER
            .comment("Entity count that a single chunk load must exceed before protection runs.")
            .defineInRange("entity_load_guard_batch_threshold", 5_000, 100, 1_000_000);
    static final ModConfigSpec.ConfigValue<Integer> ENTITY_LOAD_GUARD_TOP_TYPE_LIMIT = BUILDER
            .comment("Maximum number of the most common entity or item types reduced by protection.")
            .defineInRange("entity_load_guard_top_type_limit", 3, 1, 64);
    static final ModConfigSpec.ConfigValue<Integer> ENTITY_LOAD_GUARD_RETAIN_PER_TYPE = BUILDER
            .comment("Number of entities retained for each type selected by protection.")
            .defineInRange("entity_load_guard_retain_per_type", 100, 1, 1_000_000);
    static final ModConfigSpec.BooleanValue ENTITY_LOAD_GUARD_BROADCAST = BUILDER
            .comment("Broadcast entity load protection removals to all online players.")
            .define("entity_load_guard_broadcast", true);
    static final ModConfigSpec.BooleanValue WEAK_CHUNK_SCAN_ENABLED = BUILDER
            .comment("Enable background scans for entity-dense weak-loaded chunks.")
            .define("weak_chunk_scan_enabled", true);
    static final ModConfigSpec.BooleanValue WATCHDOG_TICKET_REPORT_ENABLED = BUILDER
            .comment("Append ServerWarashi ticket ownership diagnostics to watchdog crash reports.")
            .define("watchdog_ticket_report_enabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }
}
