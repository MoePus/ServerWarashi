package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.network.ReportDelivery;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfMessages;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 性能会话入口：负责调度单 owner 会话与全 owner 会话。
 * 上游：ServerWarashiEvents / mixin hooks / PerfCommands。
 */
public final class TicketPerfSessionController {
    private static final OwnerPerfAnalyzer ANALYZER = new OwnerPerfAnalyzer();
    private static long nextSessionId = 1L;

    private TicketPerfSessionController() {
    }

    public static Component start(ServerLevel sourceLevel,
                                  BlockPos pos,
                                  int durationSec,
                                  UUID playerId) {
        if (hasActiveSession()) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        return ANALYZER.startSingle(sourceLevel, pos, durationSec, playerId, sessionId);
    }

    public static Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        if (hasActiveSession()) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        return ANALYZER.startAll(level, durationSec, playerId, sessionId);
    }

    public static TableReport.Result stopCurrentAnalysis() {
        if (!ANALYZER.hasActiveSession()) {
            return new TableReport.Result(TicketPerfMessages.noActiveAnalysis(), null);
        }
        return ANALYZER.stopCurrentAnalysis();
    }

    public static void tickSessions(MinecraftServer server) {
        if (!ANALYZER.hasActiveSession()) {
            return;
        }
        OwnerPerfAnalyzer.AutoAnalysisReport report = ANALYZER.tickSessions(server);
        if (report != null) {
            ReportDelivery.toPlayerOrConsole(server, report.playerId(), report.result());
        }
    }

    public static long resolveTrackSessionId(Level level, BlockPos pos) {
        return ANALYZER.resolveTrackSessionId(level, pos);
    }

    public static boolean hasActiveSession() {
        return ANALYZER.hasActiveSession();
    }

    public static boolean hasActiveSession(ServerLevel level) {
        return ANALYZER.hasActiveSession(level.dimension());
    }

    public static boolean isAllOwnersSession() {
        return ANALYZER.isAllOwnersSession();
    }

    public static void clearRuntimeState() {
        ANALYZER.clearRuntimeState();
        nextSessionId = 1L;
    }

    public static void onEntityTick(BlockPos pos,
                                    String type,
                                    String sourceId,
                                    String sourceLabel,
                                    long durationNanos,
                                    boolean isBlockEntity,
                                    long sessionId) {
        if (sessionId < 0L || !ANALYZER.acceptsSessionId(sessionId)) {
            return;
        }
        ANALYZER.onEntityTick(pos, type, sourceId, sourceLabel, durationNanos, isBlockEntity);
    }

    public static void onChunkTick(ChunkPos pos, long durationNanos, long sessionId) {
        if (sessionId < 0L || !ANALYZER.acceptsSessionId(sessionId)) {
            return;
        }
        ANALYZER.onChunkTick(pos, durationNanos);
    }
}
