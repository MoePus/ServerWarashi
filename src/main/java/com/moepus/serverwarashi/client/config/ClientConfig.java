package com.moepus.serverwarashi.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<OutputMode> PERF_OUTPUT_MODE = BUILDER
            .comment("Controls how client-side structured perf reports are displayed: SCREEN, CHAT, or BOTH.")
            .defineEnum("perf_output_mode", OutputMode.SCREEN);
    private static final ModConfigSpec.ConfigValue<Integer> RESIDENT_HUD_MAX_ROWS = BUILDER
            .comment("Maximum rows drawn by the resident chunk cost HUD.")
            .defineInRange("resident_hud_max_rows", 8, 1, 32);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static OutputMode perfOutputMode() {
        return PERF_OUTPUT_MODE.get();
    }

    public static int residentHudMaxRows() {
        return RESIDENT_HUD_MAX_ROWS.get();
    }

    public enum OutputMode {
        SCREEN,
        CHAT,
        BOTH
    }
}
