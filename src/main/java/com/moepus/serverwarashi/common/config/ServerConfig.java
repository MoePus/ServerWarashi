package com.moepus.serverwarashi.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.ConfigValue<Boolean> BUCKET_ENABLED = BUILDER
            .define("bucket_enabled", false);
    static final ModConfigSpec.ConfigValue<Boolean> BUCKET_PAUSE_ALL_TICKETS = BUILDER
            .define("bucket_pause_all_tickets", false);

    static final ModConfigSpec.ConfigValue<Integer> BUCKET_RUN_EVERY = BUILDER
            .defineInRange("bucket_run_every", 10, 1, 1200);
    static final ModConfigSpec.ConfigValue<Integer> BUCKET_PHASE_PERIOD = BUILDER
            .defineInRange("bucket_phase_period", 4, 1, 1024);
    static final ModConfigSpec.ConfigValue<Integer> BUCKET_MIN_HOLD_TICKS = BUILDER
            .defineInRange("bucket_min_hold_ticks", 40, 0, 12000);
    static final ModConfigSpec.ConfigValue<Integer> BUCKET_PAUSE_DELAY_RUNS = BUILDER
            .defineInRange("bucket_pause_delay_runs", 2, 0, 1024);
    static final ModConfigSpec.ConfigValue<Integer> BUCKET_WAKE_BATCHES = BUILDER
            .defineInRange("bucket_wake_batches", 2, 1, 64);

    static final ModConfigSpec.ConfigValue<Integer> PERF_DEFAULT_ANALYZE_SECONDS = BUILDER
            .defineInRange("perf_default_analyze_seconds",
                    TicketPerfConfig.DEFAULT_ANALYZE_SECONDS,
                    1,
                    TicketPerfConfig.MAX_ANALYZE_SECONDS);

    static final ModConfigSpec.ConfigValue<Boolean> RESIDENT_CHUNK_COST_ENABLED = BUILDER
            .comment("Cost sampling is active only when the main bucket scheduler is enabled; disable to force phase scheduling.")
            .define("resident_chunk_cost_enabled", true);
    static final ModConfigSpec.ConfigValue<Integer> RESIDENT_HUD_PUSH_PERIOD = BUILDER
            .comment("Server ticks between resident cost HUD pushes to subscribed operators.")
            .defineInRange("resident_hud_push_period", 20, 1, 1200);
    static final ModConfigSpec.ConfigValue<Integer> RESIDENT_HUD_MAX_OWNERS = BUILDER
            .comment("Maximum resident cost owners included in each HUD payload.")
            .defineInRange("resident_hud_max_owners", 8, 1, 64);

    static final ModConfigSpec.ConfigValue<Boolean> IDLEFREEZE_ENABLED = BUILDER
            .define("idlefreeze_enabled", true);
    static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_OWNER_MIN_BE_PLUS_E = BUILDER
            .comment("Minimum BE+E load for idle-freezing an owner.")
            .defineInRange("idlefreeze_owner_min_be_plus_e", 1000, 0, Integer.MAX_VALUE);
    static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_INACTIVE_DAYS = BUILDER
            .defineInRange("idlefreeze_inactive_days", 1, 1, 3650);
    static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_LAST_SEEN_RETENTION_DAYS = BUILDER
            .defineInRange("idlefreeze_last_seen_retention_days", 3, 1, 3650);
    static final ModConfigSpec.ConfigValue<Integer> IDLEFREEZE_INITIAL_SCAN_DELAY_SECONDS = BUILDER
            .defineInRange("idlefreeze_initial_scan_delay_seconds", 180, 0, 3600);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {
    }
}
