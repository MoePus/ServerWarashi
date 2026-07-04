package com.moepus.serverwarashi.common.config;

public final class IdleFreezeConfig {
    private IdleFreezeConfig() {
    }

    public static boolean disabled() {
        return !Config.IDLEFREEZE_ENABLED.get();
    }

    public static int minOwnerLoad() {
        return Config.IDLEFREEZE_OWNER_MIN_BE_PLUS_E.get();
    }

    public static int inactiveDays() {
        return Config.IDLEFREEZE_INACTIVE_DAYS.get();
    }

    public static int lastSeenRetentionDays() {
        return Config.IDLEFREEZE_LAST_SEEN_RETENTION_DAYS.get();
    }

    public static int initialScanDelayTicks() {
        return Config.IDLEFREEZE_INITIAL_SCAN_DELAY_SECONDS.get() * 20;
    }
}
