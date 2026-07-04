package com.moepus.serverwarashi.common.modules.idlefreeze;

import com.moepus.serverwarashi.common.config.IdleFreezeConfig;
import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 启动扫描和按区块自动解冻。
 */
public final class IdleFreezeService {
    private IdleFreezeService() {
    }

    public static void forceRescan(ServerLevel level) {
        // 清空访问记录，使所有 owner 都按"从未访问"判定
        IdleVisitData.get(level).clear();
        runInitialScan(level);
    }

    static void reapplySavedIdlePause(ServerLevel level) {
        IdlePauseData pauseData = IdlePauseData.get(level);
        if (pauseData.isEmpty()) {
            return;
        }
        applyIdleMask(level, pauseData.allChunks(), true);
    }

    /**
     * 重放已存盘的 IDLE 暂停，裁剪过期访问记录，再把当前 owner 空闲判定结果一次性同步到 pause mask。
     */
    static void runInitialScan(ServerLevel level) {
        int todayDay = currentEpochDay();
        IdleVisitData visitData = IdleVisitData.get(level);
        IdlePauseData pauseData = IdlePauseData.get(level);

        reapplySavedIdlePause(level);
        // 保留窗口不能小于判定窗口，否则会裁掉仍需参与空闲判定的访问记录
        int retentionDays = Math.max(IdleFreezeConfig.lastSeenRetentionDays(), IdleFreezeConfig.inactiveDays());
        visitData.pruneBefore(todayDay - retentionDays);

        List<Set<Long>> targetFrozenOwners = new ArrayList<>();
        List<OwnerChunks.Entry> owners = OwnerChunks.collect(level, OwnerChunks.PauseMode.ALL);
        for (OwnerChunks.Entry owner : owners) {
            if (shouldIdleFreeze(owner, visitData, todayDay, IdleFreezeConfig.minOwnerLoad(), IdleFreezeConfig.inactiveDays())) {
                targetFrozenOwners.add(owner.chunks());
            }
        }
        reconcileIdlePause(level, pauseData, targetFrozenOwners);
    }

    public static boolean tryUnfreezeByChunk(ServerLevel level, long chunkPos) {
        IdlePauseData pauseData = IdlePauseData.get(level);
        if (pauseData.isEmpty()) {
            return false;
        }
        LongOpenHashSet removedChunks = pauseData.removeOwnersContaining(chunkPos);
        if (removedChunks.isEmpty()) {
            return false;
        }
        LongOpenHashSet chunksToClear = new LongOpenHashSet(removedChunks);
        chunksToClear.addAll(OwnerChunks.resolveChunksAt(level, chunkPos, OwnerChunks.PauseMode.ALL));
        chunksToClear.removeAll(pauseData.allChunks());
        applyIdleMask(level, chunksToClear, false);
        return true;
    }

    static int currentEpochDay() {
        // 现实日期（系统时区），不随游戏内昼夜推进
        return (int) LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    static boolean shouldIdleFreeze(OwnerChunks.Entry owner,
                                    IdleVisitData visitData,
                                    int todayDay,
                                    int minOwnerLoad,
                                    int inactiveDays) {
        OwnerChunks.Stats stats = owner.stats();
        int totalLoad = stats.blockEntityCount() + stats.entityCount();
        if (totalLoad <= minOwnerLoad) {
            return false;
        }

        int latestSeenDay = Integer.MIN_VALUE;
        for (long chunkPos : owner.chunks()) {
            latestSeenDay = Math.max(latestSeenDay, visitData.getLastSeenDay(chunkPos));
        }
        if (latestSeenDay == Integer.MIN_VALUE) {
            return true;
        }

        return todayDay - latestSeenDay > inactiveDays;
    }

    private static void reconcileIdlePause(ServerLevel level,
                                           IdlePauseData pauseData,
                                           List<Set<Long>> targetFrozenOwners) {
        LongOpenHashSet currentChunks = pauseData.allChunks();
        LongOpenHashSet targetChunks = new LongOpenHashSet();
        for (Set<Long> owner : targetFrozenOwners) {
            targetChunks.addAll(owner);
        }
        LongOpenHashSet chunksToClear = new LongOpenHashSet(currentChunks);
        chunksToClear.removeAll(targetChunks);
        LongOpenHashSet chunksToApply = new LongOpenHashSet(targetChunks);
        chunksToApply.removeAll(currentChunks);

        pauseData.replaceOwnerChunks(targetFrozenOwners);
        applyIdleMask(level, chunksToClear, false);
        applyIdleMask(level, chunksToApply, true);
    }

    private static void applyIdleMask(ServerLevel level, Set<Long> chunks, boolean paused) {
        if (chunks.isEmpty()) {
            return;
        }
        TicketPauseService.applyPauseReasonToChunks(
                level,
                chunks,
                paused,
                IPauseableTicket.PAUSE_REASON_IDLE
        );
    }
}
