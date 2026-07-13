package com.moepus.serverwarashi.common.config;

/**
 * bucket 模块配置门面。
 */
public final class TicketBucketConfig {
    private TicketBucketConfig() {
    }

    public static boolean enabled() {
        return ServerConfig.BUCKET_ENABLED.get();
    }

    public static void setEnabled(boolean enabled) {
        ServerConfig.BUCKET_ENABLED.set(enabled);
    }

    public static boolean pauseAll() {
        return ServerConfig.BUCKET_PAUSE_ALL_TICKETS.get();
    }

    public static void setPauseAll(boolean pauseAll) {
        ServerConfig.BUCKET_PAUSE_ALL_TICKETS.set(pauseAll);
    }

    public static int runEvery() {
        return ServerConfig.BUCKET_RUN_EVERY.get();
    }

    public static void setRunEvery(int runEvery) {
        ServerConfig.BUCKET_RUN_EVERY.set(runEvery);
    }

    public static int phasePeriod() {
        return ServerConfig.BUCKET_PHASE_PERIOD.get();
    }

    public static void setPhasePeriod(int phasePeriod) {
        ServerConfig.BUCKET_PHASE_PERIOD.set(phasePeriod);
    }

    public static int minHoldTicks() {
        return ServerConfig.BUCKET_MIN_HOLD_TICKS.get();
    }

    public static void setMinHoldTicks(int minHoldTicks) {
        ServerConfig.BUCKET_MIN_HOLD_TICKS.set(minHoldTicks);
    }

    public static int pauseDelayRuns() {
        return ServerConfig.BUCKET_PAUSE_DELAY_RUNS.get();
    }

    public static void setPauseDelayRuns(int pauseDelayRuns) {
        ServerConfig.BUCKET_PAUSE_DELAY_RUNS.set(pauseDelayRuns);
    }

    public static int wakeBatches() {
        return ServerConfig.BUCKET_WAKE_BATCHES.get();
    }

    public static void setWakeBatches(int wakeBatches) {
        ServerConfig.BUCKET_WAKE_BATCHES.set(wakeBatches);
    }

    public static void save() {
        ServerConfig.SPEC.save();
    }
}
