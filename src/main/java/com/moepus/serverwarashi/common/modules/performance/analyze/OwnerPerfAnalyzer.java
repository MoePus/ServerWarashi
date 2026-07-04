package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfMessages;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfReportBuilder;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfReportData;
import com.moepus.serverwarashi.common.report.TableReport;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OwnerPerfAnalyzer {
    private Session activeSession;

    Component startSingle(ServerLevel level, BlockPos pos, int durationSec, UUID playerId, long sessionId) {
        int effectiveDuration = Math.max(durationSec, 1);
        long chunkPos = new ChunkPos(pos).toLong();
        Map<TicketOwner, Set<Long>> ownersAt = OwnerChunks.resolveOwnersAt(level, chunkPos, OwnerChunks.PauseMode.ALL);
        if (ownersAt.isEmpty()) {
            return TicketPerfMessages.noOwnerAtPosition(pos.getX(), pos.getY(), pos.getZ());
        }
        if (ownersAt.size() > 1) {
            return TicketPerfMessages.multipleOwnersAtPosition(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    ownersAt.size()
            );
        }

        var single = ownersAt.entrySet().iterator().next();
        OwnerChunks.Entry owner = new OwnerChunks.Entry(
                single.getKey(),
                single.getValue(),
                OwnerChunks.stats(level, single.getValue())
        );
        activeSession = Session.single(sessionId, level.dimension(), owner, endAt(effectiveDuration), playerId);
        OwnerChunks.Stats stats = owner.stats();
        return TicketPerfMessages.sessionStarted(
                level.dimension(),
                owner.owner().asComponent(level.dimension()),
                stats.chunkCount(),
                stats.blockEntityCount(),
                stats.entityCount(),
                effectiveDuration
        );
    }

    Component startAll(ServerLevel level, int durationSec, UUID playerId, long sessionId) {
        int effectiveDuration = Math.max(durationSec, 1);
        TicketPauseService.clearAutoPause(level);
        List<OwnerChunks.Entry> owners = OwnerChunks.collect(level, OwnerChunks.PauseMode.ALL);
        if (owners.isEmpty()) {
            return TicketPerfMessages.noTicketOwnersFound();
        }

        activeSession = Session.all(sessionId, level.dimension(), owners, endAt(effectiveDuration), playerId);
        return TicketPerfMessages.allOwnersAnalysisStarted(
                level.dimension(),
                owners.size(),
                effectiveDuration
        );
    }

    boolean hasActiveSession() {
        return activeSession != null;
    }

    boolean hasActiveSession(ResourceKey<Level> dimension) {
        Session session = activeSession;
        return session != null && session.dimension.equals(dimension);
    }

    boolean acceptsSessionId(long sessionId) {
        Session session = activeSession;
        return session != null && session.id == sessionId;
    }

    boolean isAllOwnersSession() {
        Session session = activeSession;
        return session != null && session.allOwnersReport;
    }

    void clearRuntimeState() {
        activeSession = null;
    }

    TableReport.Result stopCurrentAnalysis() {
        Session session = activeSession;
        if (session == null) {
            return new TableReport.Result(TicketPerfMessages.noActiveAnalysis(), null);
        }
        // 维度消失时会话已失效，直接清空
        return stopSession(session);
    }

    AutoAnalysisReport tickSessions(MinecraftServer server) {
        Session session = activeSession;
        if (session == null) {
            return null;
        }
        session.serverTickCount++;
        if (System.nanoTime() < session.endAtNanos) {
            return null;
        }
        // 超时自动停止，维度消失时会话已失效
        return new AutoAnalysisReport(session.playerId, stopSession(session));
    }

    long resolveTrackSessionId(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null) {
            return -1L;
        }
        Session session = activeSession;
        if (session == null || !session.dimension.equals(serverLevel.dimension())) {
            return -1L;
        }
        return session.isTrackedChunk(ChunkPos.asLong(pos)) ? session.id : -1L;
    }

    void onEntityTick(Level level,
                      BlockPos pos,
                      String type,
                      String sourceId,
                      String sourceLabel,
                      long durationNanos,
                      boolean isBlockEntity) {
        if (pos == null) {
            return;
        }
        Session session = activeSession;
        if (session == null) {
            return;
        }
        recordEntityTick(session, ChunkPos.asLong(pos), type, sourceId, sourceLabel, durationNanos, isBlockEntity);
    }

    void onChunkTick(Level level, ChunkPos pos, long durationNanos) {
        Session session = activeSession;
        if (session == null) {
            return;
        }
        long chunkPos = pos.toLong();
        ChunkPerfStats stats = session.chunkStats(chunkPos);
        stats.chunkNanos += durationNanos;
    }

    private TableReport.Result stopSession(Session session) {
        activeSession = null;
        flushPending(session.blockEntityStats);
        flushPending(session.entityStats);
        return new TableReport.Result(null, session.allOwnersReport ? buildAllReport(session) : buildSingleReport(session));
    }

    private Session resolveSession(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Session session = activeSession;
        return session != null && session.dimension.equals(serverLevel.dimension()) ? session : null;
    }

    private void recordEntityTick(Session session,
                                  long chunkPos,
                                  String type,
                                  String sourceId,
                                  String sourceLabel,
                                  long durationNanos,
                                  boolean isBlockEntity) {
        ActorTimingStats stats = session.actorStats(isBlockEntity);
        if (!session.allOwnersReport) {
            stats.typeTotals.addTo(type, durationNanos);
            updateSpike(stats.spikeNanos, stats.spikeLabels, sourceId, sourceLabel, durationNanos);
        }
        if (stats.pendingChunkPos != chunkPos) {
            flushPending(stats);
            stats.pendingChunkPos = chunkPos;
        }
        stats.pendingNanos += durationNanos;
    }

    private TableReport buildAllReport(Session session) {
        return TicketPerfReportBuilder.buildOwnerMsptPerfReport(
                session.dimension,
                buildOwnerEntries(session),
                session.serverTickCount,
                Duration.ofNanos(System.nanoTime() - session.startedAtNanos),
                hasSharedChunks(session)
        );
    }

    private List<TicketPerfReportData.OwnerMsptEntry> buildOwnerEntries(Session session) {
        List<TicketPerfReportData.OwnerMsptEntry> entries = new ArrayList<>();
        Long2IntOpenHashMap ownerCounts = OwnerChunks.ownerCountByChunk(
                session.owners.stream().map(OwnerChunks.Entry::chunks).toList()
        );
        for (OwnerChunks.Entry owner : session.owners) {
            OwnerChunks.Stats stats = owner.stats();
            ChunkPerfStats totals = ownerTotals(session, owner);
            entries.add(new TicketPerfReportData.OwnerMsptEntry(
                    owner.owner(),
                    stats.chunkCount(),
                    stats.blockEntityCount(),
                    stats.entityCount(),
                    totals.blockEntityNanos + totals.entityNanos + totals.chunkNanos,
                    OwnerChunks.uniqueChunkAnchor(owner.chunks(), ownerCounts)
            ));
        }
        entries.sort(Comparator.comparingLong(TicketPerfReportData.OwnerMsptEntry::totalNanos).reversed());
        return entries;
    }

    private boolean hasSharedChunks(Session session) {
        int ownerChunkCount = 0;
        for (OwnerChunks.Entry owner : session.owners) {
            ownerChunkCount += owner.chunks().size();
        }
        return ownerChunkCount > session.trackedChunks.size();
    }

    private TableReport buildSingleReport(Session session) {
        ChunkPerfStats chunkTotals = chunkTotals(session);
        OwnerChunks.Entry owner = session.owners.getFirst();
        OwnerChunks.Stats stats = owner.stats();
        return TicketPerfReportBuilder.buildPerfReport(new TicketPerfReportData.SingleOwnerReport(
                new TicketPerfReportData.Summary(
                        session.dimension,
                        owner.owner().getName(),
                        owner.owner().representativePos(),
                        session.trackedChunks.size(),
                        stats.blockEntityCount(),
                        stats.entityCount(),
                        session.serverTickCount
                ),
                reportStats(session.blockEntityStats),
                reportStats(session.entityStats),
                new TicketPerfReportData.ChunkStats(
                        chunkTotals.chunkNanos,
                        session.chunkLoadTotals
                ),
                Duration.ofNanos(System.nanoTime() - session.startedAtNanos)
        ));
    }

    private TicketPerfReportData.ActorStats reportStats(ActorTimingStats stats) {
        return new TicketPerfReportData.ActorStats(
                stats.totalNanos,
                stats.spikeNanos,
                stats.spikeLabels,
                stats.typeTotals
        );
    }

    private ChunkPerfStats ownerTotals(Session session, OwnerChunks.Entry owner) {
        ChunkPerfStats totals = new ChunkPerfStats();
        for (long chunkPos : owner.chunks()) {
            ChunkPerfStats stats = session.chunkStats.get(chunkPos);
            if (stats == null) {
                continue;
            }
            totals.blockEntityNanos += stats.blockEntityNanos;
            totals.entityNanos += stats.entityNanos;
            totals.chunkNanos += stats.chunkNanos;
        }
        return totals;
    }

    private ChunkPerfStats chunkTotals(Session session) {
        ChunkPerfStats totals = new ChunkPerfStats();
        for (ChunkPerfStats stats : session.chunkStats.values()) {
            totals.blockEntityNanos += stats.blockEntityNanos;
            totals.entityNanos += stats.entityNanos;
            totals.chunkNanos += stats.chunkNanos;
        }
        return totals;
    }

    private void flushPending(ActorTimingStats stats) {
        long nanos = stats.pendingNanos;
        if (nanos == 0L) {
            return;
        }
        Session session = stats.session;
        if (!session.allOwnersReport) {
            stats.totalNanos += nanos;
        }
        long chunkPos = stats.pendingChunkPos;
        ChunkPerfStats chunkStats = session.chunkStats(chunkPos);
        if (stats.blockEntity) {
            chunkStats.blockEntityNanos += nanos;
        } else {
            chunkStats.entityNanos += nanos;
        }
        if (!session.allOwnersReport) {
            session.chunkLoadTotals.addTo(chunkPos, nanos);
        }
        stats.pendingChunkPos = Long.MIN_VALUE;
        stats.pendingNanos = 0L;
    }

    private void updateSpike(Object2LongOpenHashMap<String> spikeNanos,
                             Map<String, String> spikeLabels,
                             String sourceId,
                             String sourceLabel,
                             long durationNanos) {
        long current = spikeNanos.getLong(sourceId);
        if (durationNanos > current) {
            spikeNanos.put(sourceId, durationNanos);
            spikeLabels.put(sourceId, sourceLabel);
        }
    }

    private static long endAt(int durationSec) {
        return System.nanoTime() + durationSec * 1_000_000_000L;
    }

    record AutoAnalysisReport(UUID playerId, TableReport.Result result) {
    }

    private static final class Session {
        final long id;
        final ResourceKey<Level> dimension;
        final long startedAtNanos = System.nanoTime();
        final long endAtNanos;
        final UUID playerId;
        final boolean allOwnersReport;
        final List<OwnerChunks.Entry> owners;
        final LongOpenHashSet trackedChunks = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<ChunkPerfStats> chunkStats = new Long2ObjectOpenHashMap<>();
        final Long2LongOpenHashMap chunkLoadTotals = new Long2LongOpenHashMap();
        final ActorTimingStats blockEntityStats;
        final ActorTimingStats entityStats;
        long serverTickCount;
        long lastChunkPos = Long.MIN_VALUE;
        boolean lastChunkTracked;

        private Session(long id,
                        ResourceKey<Level> dimension,
                        long endAtNanos,
                        UUID playerId,
                        boolean allOwnersReport,
                        List<OwnerChunks.Entry> owners) {
            this.id = id;
            this.dimension = dimension;
            this.endAtNanos = endAtNanos;
            this.playerId = playerId;
            this.allOwnersReport = allOwnersReport;
            this.owners = owners;
            for (OwnerChunks.Entry owner : owners) {
                trackedChunks.addAll(owner.chunks());
            }
            this.blockEntityStats = new ActorTimingStats(this, true);
            this.entityStats = new ActorTimingStats(this, false);
        }

        static Session single(long id,
                              ResourceKey<Level> dimension,
                              OwnerChunks.Entry owner,
                              long endAtNanos,
                              UUID playerId) {
            return new Session(id, dimension, endAtNanos, playerId, false, List.of(owner));
        }

        static Session all(long id,
                           ResourceKey<Level> dimension,
                           List<OwnerChunks.Entry> owners,
                           long endAtNanos,
                           UUID playerId) {
            return new Session(id, dimension, endAtNanos, playerId, true, owners);
        }

        ActorTimingStats actorStats(boolean isBlockEntity) {
            return isBlockEntity ? blockEntityStats : entityStats;
        }

        boolean isTrackedChunk(long chunkPos) {
            if (chunkPos == lastChunkPos) {
                return lastChunkTracked;
            }
            boolean tracked = trackedChunks.contains(chunkPos);
            lastChunkPos = chunkPos;
            lastChunkTracked = tracked;
            return tracked;
        }

        ChunkPerfStats chunkStats(long chunkPos) {
            return chunkStats.computeIfAbsent(chunkPos, ignored -> new ChunkPerfStats());
        }
    }

    private static final class ActorTimingStats {
        final Session session;
        final boolean blockEntity;
        final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
        final Object2LongOpenHashMap<String> spikeNanos = new Object2LongOpenHashMap<>();
        final Map<String, String> spikeLabels = new HashMap<>();
        long totalNanos;
        // 按 chunk 暂存待冲账的纳秒耗时；MIN_VALUE 表示当前无暂存 chunk
        long pendingChunkPos = Long.MIN_VALUE;
        long pendingNanos;

        ActorTimingStats(Session session, boolean blockEntity) {
            this.session = session;
            this.blockEntity = blockEntity;
        }
    }
}
