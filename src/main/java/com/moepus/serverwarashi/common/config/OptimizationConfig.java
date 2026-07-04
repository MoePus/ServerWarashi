package com.moepus.serverwarashi.common.config;

public final class OptimizationConfig {
    private OptimizationConfig() {
    }

    public static boolean residentChunkCostEnabled() {
        return TicketBucketConfig.enabled() && Config.RESIDENT_CHUNK_COST_ENABLED.get();
    }

    public static int residentHudPushPeriod() {
        return Config.RESIDENT_HUD_PUSH_PERIOD.get();
    }

    public static int residentHudMaxOwners() {
        return Config.RESIDENT_HUD_MAX_OWNERS.get();
    }
}
