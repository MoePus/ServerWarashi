package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import com.moepus.serverwarashi.common.core.ticket.IPauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketOwner;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfMessages;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfReportBuilder;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfReportData;
import com.moepus.serverwarashi.common.report.TableReport;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
    static final long NO_SESSION_ID = -1L;
    private Session activeSession;

    Component startSingle(ServerLevel level, BlockPos pos, int durationSec, UUID playerId, long sessionId) {
        long chunkPos = new ChunkPos(pos).toLong();
        Map<TicketOwner, Set<Long>> ownersAt = OwnerChunks.resolveOwnersAt(level, chunkPos, OwnerChunks.PauseMode.ALL);
        if (ownersAt.isEmpty()) {
            return TicketPerfMessages.noOwnerAtPosition(pos.getX(), pos.getY(), pos.getZ());
        }
        if (ownersAt.size() > 1) {
            return TicketPerfMessages.multipleOwnersAtPosition(pos.getX(), pos.getY(), pos.getZ(), ownersAt.size());
        }

        var single = ownersAt.entrySet().iterator().next();
        TicketPauseService.applyPauseReasonToChunks(
                level,
                single.getValue(),
                false,
                IPauseableTicket.PAUSE_REASON_AUTO
        );
        OwnerChunks.Entry owner = new OwnerChunks.Entry(
                single.getKey(),
                single.getValue(),
                OwnerChunks.stats(level, single.getValue())
        );
        activeSession = Session.single(sessionId, level.dimension(), owner, endAt(durationSec), playerId);
        OwnerChunks.Stats stats = owner.stats();
        return TicketPerfMessages.sessionStarted(
                level.dimension(),
                owner.owner().asComponent(level.dimension()),
                stats.chunkCount(),
                stats.blockEntityCount(),
                stats.entityCount(),
                durationSec
        );
    }

    Component startAll(ServerLevel level, int durationSec, UUID playerId, long sessionId) {
        TicketPauseService.clearAutoPause(level);
        List<OwnerChunks.Entry> owners = OwnerChunks.collect(level, OwnerChunks.PauseMode.ALL);
        if (owners.isEmpty()) {
            return TicketPerfMessages.noTicketOwnersFound();
        }

        activeSession = Session.all(sessionId, level.dimension(), owners, endAt(durationSec), playerId);
        return TicketPerfMessages.allOwnersAnalysisStarted(
                level.dimension(),
                owners.size(),
                durationSec
        );
    }

    long activeSessionId() {
        Session session = activeSession;
        return session == null ? NO_SESSION_ID : session.id;
    }

    long activeSessionIdIn(ResourceKey<Level> dimension) {
        Session session = activeSession;
        return session != null && session.dimension.equals(dimension) ? session.id : NO_SESSION_ID;
    }

    boolean isAllOwnersSession() {
        Session session = activeSession;
        return session != null && session.allOwnersReport;
    }

    void clearRuntimeState() {
        activeSession = null;
    }

    TableReport.Result stopCurrentAnalysis() {
        return stopSession(activeSession);
    }

    AutoAnalysisReport tickSessions() {
        Session session = activeSession;
        session.serverTickCount++;
        if (System.nanoTime() < session.endAtNanos) {
            return null;
        }
        return new AutoAnalysisReport(session.playerId, stopSession(session));
    }

    long resolveTrackSessionId(ServerLevel level, BlockPos pos) {
        Session session = activeSession;
        if (!session.dimension.equals(level.dimension())) {
            return NO_SESSION_ID;
        }
        return session.isTrackedChunk(ChunkPos.asLong(pos)) ? session.id : NO_SESSION_ID;
    }

    void onEntityTick(BlockPos pos,
                      String type,
                      String sourceId,
                      String sourceLabel,
                      long durationNanos,
                      boolean isBlockEntity) {
        Session session = activeSession;
        long chunkPos = ChunkPos.asLong(pos);
        ActorTimingStats stats = session.actorStats(isBlockEntity);
        if (!session.allOwnersReport) {
            stats.typeTotals.addTo(type, durationNanos);
            long currentSpikeNanos = stats.spikeNanos.getLong(sourceId);
            if (durationNanos > currentSpikeNanos) {
                stats.spikeNanos.put(sourceId, durationNanos);
                stats.spikeLabels.put(sourceId, sourceLabel);
            }
        }
        if (stats.pendingChunkPos != chunkPos) {
            flushPending(stats);
            stats.pendingChunkPos = chunkPos;
        }
        stats.pendingNanos += durationNanos;
    }

    void onChunkTick(ChunkPos pos, long durationNanos) {
        Session session = activeSession;
        session.chunkTickNanos.addTo(pos.toLong(), durationNanos);
    }

    private TableReport.Result stopSession(Session session) {
        activeSession = null;
        flushPending(session.blockEntityStats);
        flushPending(session.entityStats);
        return new TableReport.Result(null, session.allOwnersReport ? buildAllReport(session) : buildSingleReport(session));
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
            entries.add(new TicketPerfReportData.OwnerMsptEntry(
                    owner.owner(),
                    stats.chunkCount(),
                    stats.blockEntityCount(),
                    stats.entityCount(),
                    ownerTotalNanos(session, owner),
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
        long chunkTickTotal = 0L;
        for (long nanos : session.chunkTickNanos.values()) {
            chunkTickTotal += nanos;
        }
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
                        chunkTickTotal,
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

    private long ownerTotalNanos(Session session, OwnerChunks.Entry owner) {
        long total = 0L;
        for (long chunkPos : owner.chunks()) {
            total += session.chunkTickNanos.get(chunkPos)
                    + session.chunkBlockEntityNanos.get(chunkPos)
                    + session.chunkEntityNanos.get(chunkPos);
        }
        return total;
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
        (stats.blockEntity ? session.chunkBlockEntityNanos : session.chunkEntityNanos).addTo(chunkPos, nanos);
        if (!session.allOwnersReport) {
            session.chunkLoadTotals.addTo(chunkPos, nanos);
        }
        stats.pendingChunkPos = Long.MIN_VALUE;
        stats.pendingNanos = 0L;
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
        final Long2LongOpenHashMap chunkTickNanos = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap chunkBlockEntityNanos = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap chunkEntityNanos = new Long2LongOpenHashMap();
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
    }

    private static final class ActorTimingStats {
        final Session session;
        final boolean blockEntity;
        final Object2LongOpenHashMap<String> typeTotals = new Object2LongOpenHashMap<>();
        final Object2LongOpenHashMap<String> spikeNanos = new Object2LongOpenHashMap<>();
        final Map<String, String> spikeLabels = new HashMap<>();
        long totalNanos;
        // 按 chunk 暂存尚未计入汇总的纳秒耗时；MIN_VALUE 表示当前没有暂存中的 chunk
        long pendingChunkPos = Long.MIN_VALUE;
        long pendingNanos;

        ActorTimingStats(Session session, boolean blockEntity) {
            this.session = session;
            this.blockEntity = blockEntity;
        }
    }
}
