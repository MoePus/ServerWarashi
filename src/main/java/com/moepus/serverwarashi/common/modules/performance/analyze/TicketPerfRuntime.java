package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.network.ReportDelivery;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfMessages;
import com.moepus.serverwarashi.common.report.TableReport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * 性能会话入口：负责调度单 owner 会话与全 owner 会话。
 * 上游：ServerWarashiEvents / mixin hooks / TicketPerfCommands。
 */
public final class TicketPerfRuntime {
    public static final long NO_SESSION_ID = TicketPerfAnalyzer.NO_SESSION_ID;
    private static final TicketPerfAnalyzer ANALYZER = new TicketPerfAnalyzer();
    private static long nextSessionId = 1L;

    private TicketPerfRuntime() {
    }

    public static Component start(ServerLevel sourceLevel,
                                  BlockPos pos,
                                  int durationSec,
                                  UUID playerId) {
        if (activeSessionId() != NO_SESSION_ID) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        return ANALYZER.startSingle(sourceLevel, pos, durationSec, playerId, sessionId);
    }

    public static Component startAll(ServerLevel level, int durationSec, UUID playerId) {
        if (activeSessionId() != NO_SESSION_ID) {
            return TicketPerfMessages.analysisAlreadyRunning();
        }
        long sessionId = nextSessionId++;
        return ANALYZER.startAll(level, durationSec, playerId, sessionId);
    }

    public static StopResult stopCurrentAnalysis() {
        if (activeSessionId() == NO_SESSION_ID) {
            return new StopResult(TicketPerfMessages.noActiveAnalysis(), null);
        }
        return new StopResult(null, ANALYZER.stopActiveSession());
    }

    /** 停止分析的结果信封：message 与 report 恰有一个非空。 */
    public record StopResult(Component message, TableReport report) {
    }

    public static void tickSessions(MinecraftServer server) {
        if (activeSessionId() == NO_SESSION_ID) {
            return;
        }
        TicketPerfAnalyzer.AutoAnalysisReport report = ANALYZER.tickSessions();
        if (report != null) {
            ReportDelivery.toPlayerOrConsole(server, report.playerId(), report.report());
        }
    }

    public static long resolveTrackSessionId(ServerLevel level, BlockPos pos) {
        return ANALYZER.resolveTrackSessionId(level, pos);
    }

    private static long activeSessionId() {
        return ANALYZER.activeSessionId();
    }

    public static boolean hasActiveSessionIn(ServerLevel level) {
        return ANALYZER.activeSessionIdIn(level.dimension()) != NO_SESSION_ID;
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
        long activeSessionId = activeSessionId();
        // Public hook entry: ignore stale callbacks after a session stops or changes.
        if (activeSessionId == NO_SESSION_ID || sessionId != activeSessionId) {
            return;
        }
        ANALYZER.onEntityTick(pos, type, sourceId, sourceLabel, durationNanos, isBlockEntity);
    }

    public static void onChunkTick(ChunkPos pos, long durationNanos, long sessionId) {
        long activeSessionId = activeSessionId();
        // Public hook entry: ignore stale callbacks after a session stops or changes.
        if (activeSessionId == NO_SESSION_ID || sessionId != activeSessionId) {
            return;
        }
        ANALYZER.onChunkTick(pos, durationNanos);
    }
}
