package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TicketPerfRuntime {
    private static final PerfSessionController CONTROLLER = new PerfSessionController();

    private TicketPerfRuntime() {
    }

    public static StartResult start(MinecraftServer server, int durationSeconds, UUID playerId) {
        if (!CONTROLLER.start(server, durationSeconds, playerId)) {
            return new StartResult(Component.literal("Another perf analysis is already running."), false);
        }
        return new StartResult(Component.literal("Performance analysis started for " + durationSeconds
                + "s; tick sampling begins on the next server tick."), true);
    }

    public static void onServerTickPre(MinecraftServer server) {
        CONTROLLER.onServerTickPre(server);
    }

    public static AutoReport onServerTickPost(MinecraftServer server) {
        PerfSessionController.Completion completion = CONTROLLER.onServerTickPost(server);
        if (completion == null) {
            return null;
        }
        return new AutoReport(completion.playerId(), completion.data());
    }

    public static StopResult stopCurrentAnalysis() {
        return stopCurrentAnalysis(false);
    }

    public static StopResult stopCurrentAnalysis(MinecraftServer server) {
        return stopCurrentAnalysis(server != null && server.isPaused());
    }

    private static StopResult stopCurrentAnalysis(boolean retainInitialLayout) {
        PerfReportData data = CONTROLLER.stop(retainInitialLayout);
        if (data != null) {
            return new StopResult(null, data);
        }
        return new StopResult(Component.literal("No active perf analysis."), null);
    }

    public static boolean isProfiling() {
        return CONTROLLER.hasActiveSession();
    }

    public static PerfReportData currentReport() {
        PerfReportData report = currentReportOrNull();
        return report == null ? PerfReportData.empty(TicketPerfConfig.sampleIntervalMicros()) : report;
    }

    public static PerfReportData currentReportOrNull() {
        return CONTROLLER.currentReport();
    }

    public static boolean clearLastReport() {
        return CONTROLLER.clearLastReport();
    }

    public static void clearRuntimeState() {
        CONTROLLER.clear();
    }

    public record AutoReport(UUID playerId, PerfReportData data) {
    }

    public record StartResult(Component message, boolean started) {
    }

    public record StopResult(Component message, PerfReportData data) {
    }
}
