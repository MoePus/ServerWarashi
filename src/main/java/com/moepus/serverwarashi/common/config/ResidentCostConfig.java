package com.moepus.serverwarashi.common.config;

public final class ResidentCostConfig {
    private ResidentCostConfig() {
    }

    public static boolean residentChunkCostEnabled() {
        return TicketBucketConfig.enabled() && ServerConfig.RESIDENT_CHUNK_COST_ENABLED.get();
    }

    public static int residentHudPushPeriod() {
        return ServerConfig.RESIDENT_HUD_PUSH_PERIOD.get();
    }

    public static int residentHudMaxOwners() {
        return ServerConfig.RESIDENT_HUD_MAX_OWNERS.get();
    }
}
