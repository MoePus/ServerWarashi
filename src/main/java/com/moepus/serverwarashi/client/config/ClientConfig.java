package com.moepus.serverwarashi.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<OutputMode> PERF_OUTPUT_MODE = BUILDER
            .comment("Controls how client-side structured perf reports are displayed: SCREEN, CHAT, or BOTH.")
            .defineEnum("perf_output_mode", OutputMode.SCREEN);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static OutputMode perfOutputMode() {
        return PERF_OUTPUT_MODE.get();
    }

    public enum OutputMode {
        SCREEN,
        CHAT,
        BOTH
    }
}
