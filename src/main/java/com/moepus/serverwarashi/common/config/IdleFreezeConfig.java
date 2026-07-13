package com.moepus.serverwarashi.common.config;

public final class IdleFreezeConfig {
    private IdleFreezeConfig() {
    }

    public static boolean disabled() {
        return !ServerConfig.IDLEFREEZE_ENABLED.get();
    }

    public static int minOwnerLoad() {
        return ServerConfig.IDLEFREEZE_OWNER_MIN_BE_PLUS_E.get();
    }

    public static int inactiveDays() {
        return ServerConfig.IDLEFREEZE_INACTIVE_DAYS.get();
    }

    public static int lastSeenRetentionDays() {
        return ServerConfig.IDLEFREEZE_LAST_SEEN_RETENTION_DAYS.get();
    }

    public static int initialScanDelayTicks() {
        return ServerConfig.IDLEFREEZE_INITIAL_SCAN_DELAY_SECONDS.get() * 20;
    }
}
