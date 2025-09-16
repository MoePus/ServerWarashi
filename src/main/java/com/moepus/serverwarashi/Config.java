package com.moepus.serverwarashi;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Boolean> ENABLED = BUILDER
            .define("enabled", false);
    public static final ModConfigSpec.ConfigValue<Boolean> PAUSE_ALL_TICKETS = BUILDER
            .define("pause_all_tickets", false);

    public static final ModConfigSpec.ConfigValue<Integer> TICKET_GROUP_SIZE = BUILDER
            .defineInRange("ticket_group_size", 48, 16, 2048);
    public static final ModConfigSpec.ConfigValue<Integer> RUN_EVERY = BUILDER
            .defineInRange("run_every", 10, 1, 1200);
    public static final ModConfigSpec.ConfigValue<Integer> PROXIMITY_THRESHOLD = BUILDER
            .defineInRange("proximity_threshold", 5, 1, 12);

    static final ModConfigSpec SPEC = BUILDER.build();
}
