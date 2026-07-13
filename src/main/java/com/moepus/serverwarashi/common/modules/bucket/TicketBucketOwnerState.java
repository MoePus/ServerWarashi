package com.moepus.serverwarashi.common.modules.bucket;

final class TicketBucketOwnerState {
    boolean paused;
    int inactiveRuns;
    int wakeBatchIndex = -1;
    long lastFlipTick;
    long lastActiveCycle = -1L;
    final long firstSeenCycle;
    // 最近一次在候选集中出现的 cycle；调度器据此只回收长期缺席的 owner 状态，避免瞬时消失的 owner 立刻丢失滞后状态。
    long lastSeenCycle;

    TicketBucketOwnerState(long age, long firstSeenCycle) {
        this.lastFlipTick = age;
        this.firstSeenCycle = firstSeenCycle;
        this.lastSeenCycle = firstSeenCycle;
    }

    boolean isWaking() {
        return wakeBatchIndex >= 0;
    }

    void beginWaking(long age) {
        wakeBatchIndex = 0;
        lastFlipTick = age;
    }

    void markActiveWithoutFlip() {
        paused = false;
        wakeBatchIndex = -1;
        inactiveRuns = 0;
    }

    void markActiveAfterWake(long age) {
        markActiveWithoutFlip();
        lastFlipTick = age;
    }

    void markPaused(long age, boolean changed) {
        paused = true;
        wakeBatchIndex = -1;
        inactiveRuns = 0;
        if (changed) {
            lastFlipTick = age;
        }
    }
}
