package com.moepus.serverwarashi.common.modules.bucket;

import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwnerKey;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseBatch;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.config.TicketBucketConfig;
import com.moepus.serverwarashi.common.modules.cost.ChunkCostRuntime;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owner-phase 调度器：按稳定 owner hash 分相位，并对 promote/demote 做滞后。
 */
final class TicketBucketScheduler {
    private final Map<ResourceKey<Level>, Map<TicketOwnerKey, TicketBucketOwnerState>> statesByLevel = new HashMap<>();

    void schedule(ServerLevel level,
                  List<TicketBucketService.TicketEntry> tickets,
                  long age,
                  TicketPauseBatch pauseBatch) {
        Map<TicketOwnerKey, List<TicketBucketService.TicketEntry>> ownerTickets = ownerTickets(tickets);
        Map<TicketOwnerKey, TicketBucketOwnerState> states =
                statesByLevel.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());

        SchedulerConfig config = SchedulerConfig.read(age);
        // 只回收缺席超过保留周期数（tombstoneTtlCycles）的 owner 状态，短暂缺席者保留（bug-02）
        pruneStaleStates(states, ownerTickets.keySet(), config.cycle(),
                tombstoneTtlCycles(config.minHoldTicks(), config.runEvery()));
        CostAwareSelection costAwareSelection = ChunkCostRuntime.hasCostData(level)
                ? selectCostAwareActiveOwners(level, ownerTickets, states, config)
                : CostAwareSelection.none();
        boolean costAware = costAwareSelection.hasKnownCostData();
        for (Map.Entry<TicketOwnerKey, List<TicketBucketService.TicketEntry>> entry : ownerTickets.entrySet()) {
            TicketBucketOwnerState state = states.computeIfAbsent(entry.getKey(), k -> new TicketBucketOwnerState(config.age(), config.cycle()));
            boolean active = costAware
                    ? costAwareSelection.activeOwners().contains(entry.getKey())
                    : isActivePhase(entry.getKey(), config.cycle(), config.period());
            applyOwnerTickets(entry.getValue(), state, config, active, pauseBatch);
        }
    }

    void clear() {
        statesByLevel.clear();
    }

    static long cycle(long age, int runEvery) {
        return Math.floorDiv(age, Math.max(1, runEvery));
    }

    static boolean isActivePhase(Object owner, long cycle, int period) {
        int normalizedPeriod = Math.max(1, period);
        return phaseForNormalized(owner, normalizedPeriod) == Math.floorMod(cycle, normalizedPeriod);
    }

    private static int phaseForNormalized(Object owner, int normalizedPeriod) {
        return Math.floorMod(owner.hashCode(), normalizedPeriod);
    }

    static boolean pauseAllowed(int inactiveRuns,
                                long age,
                                long lastFlipTick,
                                int pauseDelayRuns,
                                int minHoldTicks) {
        return inactiveRuns > Math.max(0, pauseDelayRuns)
                && age - lastFlipTick >= Math.max(0, minHoldTicks);
    }

    /**
     * 墓碑+TTL 回收：本轮出现的 owner 刷新 lastSeenCycle；缺席 owner 仅在超过 ttlCycles 后才回收，
     * 期间保留其滞后状态（lastFlipTick/lastActiveCycle/wakeBatchIndex），避免瞬时消失→重建导致的暂停↔唤醒抖动
     */
    static <K> void pruneStaleStates(Map<K, TicketBucketOwnerState> states,
                                     Set<K> presentOwners,
                                     long cycle,
                                     long ttlCycles) {
        Iterator<Map.Entry<K, TicketBucketOwnerState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, TicketBucketOwnerState> entry = iterator.next();
            if (presentOwners.contains(entry.getKey())) {
                entry.getValue().lastSeenCycle = cycle;
            } else if (cycle - entry.getValue().lastSeenCycle > ttlCycles) {
                iterator.remove();
            }
        }
    }

    static long tombstoneTtlCycles(int minHoldTicks, int runEvery) {
        return Math.max(2L, Math.ceilDiv(Math.max(0, minHoldTicks), Math.max(1, runEvery)) + 1L);
    }

    private static Map<TicketOwnerKey, List<TicketBucketService.TicketEntry>> ownerTickets(
            List<TicketBucketService.TicketEntry> tickets
    ) {
        Map<TicketOwnerKey, List<TicketBucketService.TicketEntry>> ownerTickets = new HashMap<>();
        for (TicketBucketService.TicketEntry entry : tickets) {
            ownerTickets.computeIfAbsent(entry.ownerKey(), ignored -> new ArrayList<>()).add(entry);
        }
        return ownerTickets;
    }

    private CostAwareSelection selectCostAwareActiveOwners(ServerLevel level,
                                                           Map<TicketOwnerKey, List<TicketBucketService.TicketEntry>> ownerTickets,
                                                           Map<TicketOwnerKey, TicketBucketOwnerState> states,
                                                           SchedulerConfig config) {
        int budget = activeOwnerBudget(ownerTickets.size(), config.period());
        boolean hasKnownCostData = false;
        List<CostCandidate> candidates = new ArrayList<>();
        for (Map.Entry<TicketOwnerKey, List<TicketBucketService.TicketEntry>> entry : ownerTickets.entrySet()) {
            OwnerCost ownerCost = ownerCost(level, entry.getValue());
            hasKnownCostData |= ownerCost.hasKnownCostData();
            candidates.add(new CostCandidate(
                    entry.getKey(),
                    costAwareScore(states.get(entry.getKey()), config, ownerCost.costNanos())
            ));
        }
        if (!hasKnownCostData) {
            return CostAwareSelection.none();
        }
        // 每个 owner 算分(年龄优先−成本惩罚)，按分降序排序，同分先比稳定键再比 hash 定先后，取前 budget 名为活跃
        Set<TicketOwnerKey> activeOwners = candidates.stream()
                .sorted(Comparator.comparingLong(CostCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.owner().stableSortKey())
                        .thenComparingInt(candidate -> candidate.owner().hashCode()))
                .limit(budget)
                .map(CostCandidate::owner)
                .collect(Collectors.toCollection(HashSet::new));
        return new CostAwareSelection(activeOwners, true);
    }

    // 成本只作为同一周期内的细分排序；多等待一个空闲周期一定压过最大成本惩罚。
    private static final long COST_PENALTY_BUCKET_NANOS = 100_000L;
    private static final long MAX_COST_PENALTY = 999L;
    private static final long AGE_PRIORITY_WEIGHT = MAX_COST_PENALTY + 1L;

    static long score(long cyclesSinceActive, long ownerCost) {
        long cycles = Math.max(0L, cyclesSinceActive);
        long agePriority = cycles > Long.MAX_VALUE / AGE_PRIORITY_WEIGHT
                ? Long.MAX_VALUE
                : cycles * AGE_PRIORITY_WEIGHT;
        long normalizedCost = Math.max(0L, ownerCost);
        long costPenalty = Math.min(MAX_COST_PENALTY, normalizedCost / COST_PENALTY_BUCKET_NANOS);
        return agePriority - costPenalty;
    }

    private long costAwareScore(TicketBucketOwnerState state,
                                SchedulerConfig config,
                                long ownerCost) {
        // 推算"距上次活跃的 cycle 数"作为年龄输入
        long cyclesSinceActive;
        if (state == null) {
            cyclesSinceActive = config.period();
        } else if (state.lastActiveCycle < 0L) {
            cyclesSinceActive = Math.max(0L, config.cycle() - state.firstSeenCycle) + config.period();
        } else {
            cyclesSinceActive = Math.max(0L, config.cycle() - state.lastActiveCycle);
        }
        return score(cyclesSinceActive, ownerCost);
    }

    private OwnerCost ownerCost(ServerLevel level, List<TicketBucketService.TicketEntry> entries) {
        long cost = 0L;
        boolean hasKnownCostData = false;
        LongOpenHashSet seenChunks = new LongOpenHashSet();
        for (TicketBucketService.TicketEntry entry : entries) {
            if (!seenChunks.add(entry.chunkPos())) {
                continue;
            }
            ChunkCostRuntime.CostSample sample = ChunkCostRuntime.costSampleForChunk(level, entry.chunkPos());
            if (!sample.hasKnownCost()) {
                continue;
            }
            hasKnownCostData = true;
            long chunkCost = sample.totalNanos();
            if (chunkCost > 0L) {
                cost += chunkCost;
            }
        }
        return new OwnerCost(cost, hasKnownCostData);
    }

    static int activeOwnerBudget(int ownerCount, int period) {
        return Math.max(1, Math.ceilDiv(ownerCount, Math.max(1, period)));
    }

    private void applyOwnerTickets(List<TicketBucketService.TicketEntry> entries,
                                   TicketBucketOwnerState state,
                                   SchedulerConfig config,
                                   boolean active,
                                   TicketPauseBatch pauseBatch) {
        if (state.isWaking()) {
            wakeNextBatch(entries, state, config, pauseBatch);
            return;
        }

        if (active) {
            state.lastActiveCycle = config.cycle();
            requestActive(entries, state, config, pauseBatch);
        } else {
            requestPaused(entries, state, config, pauseBatch);
        }
    }

    private void requestActive(List<TicketBucketService.TicketEntry> entries,
                               TicketBucketOwnerState state,
                               SchedulerConfig config,
                               TicketPauseBatch pauseBatch) {
        state.inactiveRuns = 0;
        if (!syncPausedStateFromTickets(entries, state)) {
            return;
        }
        if (config.age() - state.lastFlipTick < config.minHoldTicks()) {
            return;
        }
        state.beginWaking(config.age());
        wakeNextBatch(entries, state, config, pauseBatch);
    }

    private boolean syncPausedStateFromTickets(List<TicketBucketService.TicketEntry> entries,
                                               TicketBucketOwnerState state) {
        for (TicketBucketService.TicketEntry entry : entries) {
            if ((TicketUtils.asPauseable(entry.ticket()).serverWarashi$getPauseMask()
                    & IPauseableTicket.PAUSE_REASON_AUTO) != 0) {
                state.paused = true;
                return true;
            }
        }
        if (state.paused) {
            state.markActiveWithoutFlip();
        }
        return false;
    }

    private void requestPaused(List<TicketBucketService.TicketEntry> entries,
                               TicketBucketOwnerState state,
                               SchedulerConfig config,
                               TicketPauseBatch pauseBatch) {
        state.inactiveRuns++;
        if (state.paused || !pauseAllowed(
                state.inactiveRuns,
                config.age(),
                state.lastFlipTick,
                config.pauseDelayRuns(),
                config.minHoldTicks()
        )) {
            return;
        }
        boolean changed = pauseAll(entries, pauseBatch);
        state.markPaused(config.age(), changed);
    }

    private void wakeNextBatch(List<TicketBucketService.TicketEntry> entries,
                               TicketBucketOwnerState state,
                               SchedulerConfig config,
                               TicketPauseBatch pauseBatch) {
        // 批数取 min(配置批数, owner 内 ticket 数)，避免空批
        int batches = Math.min(Math.max(1, config.wakeBatches()), entries.size());
        // 本轮处理第 wakeBatchIndex 批：解除该批 ticket 的 AUTO 暂停；分批恢复，避免全部 chunk 在同一 tick 恢复造成瞬时卡顿
        int batch = state.wakeBatchIndex % batches;
        for (int i = 0; i < entries.size(); i++) {
            if (i % batches == batch) {
                applyPause(entries.get(i), false, pauseBatch);
            }
        }
        state.wakeBatchIndex++;
        if (state.wakeBatchIndex >= batches) {
            state.markActiveAfterWake(config.age());
        }
    }

    private boolean pauseAll(List<TicketBucketService.TicketEntry> entries,
                             TicketPauseBatch pauseBatch) {
        boolean changed = false;
        for (TicketBucketService.TicketEntry entry : entries) {
            changed |= applyPause(entry, true, pauseBatch);
        }
        return changed;
    }

    private boolean applyPause(TicketBucketService.TicketEntry entry,
                               boolean paused,
                               TicketPauseBatch pauseBatch) {
        return pauseBatch.apply(entry.ticket(), entry.chunkPos(), paused, IPauseableTicket.PAUSE_REASON_AUTO);
    }

    private record CostCandidate(TicketOwnerKey owner, long score) {
    }

    private record CostAwareSelection(Set<TicketOwnerKey> activeOwners, boolean hasKnownCostData) {
        private static CostAwareSelection none() {
            return new CostAwareSelection(Set.of(), false);
        }
    }

    private record OwnerCost(long costNanos, boolean hasKnownCostData) {
    }

    private record SchedulerConfig(
            long age,
            long cycle,
            int period,
            int minHoldTicks,
            int pauseDelayRuns,
            int wakeBatches,
            int runEvery
    ) {
        private static SchedulerConfig read(long age) {
            return new SchedulerConfig(
                    age,
                    TicketBucketScheduler.cycle(age, TicketBucketConfig.runEvery()),
                    TicketBucketConfig.phasePeriod(),
                    TicketBucketConfig.minHoldTicks(),
                    TicketBucketConfig.pauseDelayRuns(),
                    TicketBucketConfig.wakeBatches(),
                    TicketBucketConfig.runEvery()
            );
        }
    }
}
