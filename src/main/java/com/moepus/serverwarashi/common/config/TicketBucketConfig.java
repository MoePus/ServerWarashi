package com.moepus.serverwarashi.common.config;

/**
 * bucket 模块配置门面。
 */
public final class TicketBucketConfig {
    private TicketBucketConfig() {
    }

    public static boolean enabled() {
        return Config.ENABLED.get();
    }

    public static void setEnabled(boolean enabled) {
        Config.ENABLED.set(enabled);
    }

    public static boolean pauseAll() {
        return Config.PAUSE_ALL_TICKETS.get();
    }

    public static void setPauseAll(boolean pauseAll) {
        Config.PAUSE_ALL_TICKETS.set(pauseAll);
    }

    public static int runEvery() {
        return Config.RUN_EVERY.get();
    }

    public static void setRunEvery(int runEvery) {
        Config.RUN_EVERY.set(runEvery);
    }

    public static int phasePeriod() {
        return Config.TICKET_PHASE_PERIOD.get();
    }

    public static void setPhasePeriod(int phasePeriod) {
        Config.TICKET_PHASE_PERIOD.set(phasePeriod);
    }

    public static int minHoldTicks() {
        return Config.TICKET_MIN_HOLD_TICKS.get();
    }

    public static void setMinHoldTicks(int minHoldTicks) {
        Config.TICKET_MIN_HOLD_TICKS.set(minHoldTicks);
    }

    public static int pauseDelayRuns() {
        return Config.TICKET_PAUSE_DELAY_RUNS.get();
    }

    public static void setPauseDelayRuns(int pauseDelayRuns) {
        Config.TICKET_PAUSE_DELAY_RUNS.set(pauseDelayRuns);
    }

    public static int wakeBatches() {
        return Config.TICKET_WAKE_BATCHES.get();
    }

    public static void setWakeBatches(int wakeBatches) {
        Config.TICKET_WAKE_BATCHES.set(wakeBatches);
    }

    public static void save() {
        Config.SPEC.save();
    }
}
