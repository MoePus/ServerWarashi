package com.moepus.serverwarashi.client.config;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<OutputMode> PERF_OUTPUT_MODE = BUILDER
            .comment("Controls how client-side structured perf reports are displayed: SCREEN, CHAT, or BOTH.")
            .defineEnum("perf_output_mode", OutputMode.SCREEN);
    private static final ModConfigSpec.EnumValue<WorldOverlayFilter> WORLD_OVERLAY_FILTER = BUILDER
            .comment("Controls whether the world perf overlay displays all sampled objects or effective sample strengths of two and above.")
            .defineEnum("world_overlay_filter", WorldOverlayFilter.TWO_OR_MORE);
    private static final ModConfigSpec.BooleanValue WORLD_OVERLAY_ENABLED = BUILDER
            .comment("Enable the in-world performance overlay.")
            .define("world_overlay_enabled", true);
    private static final ModConfigSpec.IntValue WORLD_OVERLAY_MARKER_LIMIT = BUILDER
            .comment("Maximum world-overlay markers uploaded to the GPU each frame.")
            .defineInRange("world_overlay_marker_limit", 4_096, 1_024,
                    PerfReportLimits.MAX_WORLD_HOTSPOT_ENTRIES);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static OutputMode perfOutputMode() {
        return PERF_OUTPUT_MODE.get();
    }

    public static WorldOverlayFilter worldOverlayFilter() {
        return WORLD_OVERLAY_FILTER.get();
    }

    public static boolean worldOverlayEnabled() {
        return WORLD_OVERLAY_ENABLED.get();
    }

    public static int worldOverlayMarkerLimit() {
        return WORLD_OVERLAY_MARKER_LIMIT.get();
    }

    public enum OutputMode {
        SCREEN,
        CHAT,
        BOTH
    }

    public enum WorldOverlayFilter {
        ALL,
        TWO_OR_MORE
    }
}
