package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfSamplerSession;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

final class PerfSessionController {
    private static final int TICKS_PER_SECOND = 20;
    private static final int ATTRIBUTION_REFRESH_TICKS = 20;

    private PendingSession pending;
    private ActiveSession active;
    private PerfReportData lastReport;
    private long nextSessionId = 1L;

    boolean schedule(int durationSeconds, UUID playerId) {
        if (pending != null || active != null) {
            return false;
        }
        pending = new PendingSession(durationSeconds * (long) TICKS_PER_SECOND, playerId);
        return true;
    }

    void onServerTickPre(MinecraftServer server) {
        PerfWorkTracker.clearCurrent();
        try {
            if (active == null && pending != null) {
                activatePending();
            }
            beginTick(server);
        } catch (RuntimeException | Error exception) {
            if (active != null) {
                stopActive();
            }
            throw exception;
        }
    }

    Completion onServerTickPost(MinecraftServer server) {
        try {
            ActiveSession session = active;
            if (session == null || !session.tickOpen) {
                return null;
            }
            session.sampler.endTick(currentTickNanos(server));
            PerfWorkTracker.clearCurrent();
            session.tickOpen = false;
            session.completedTicks++;
            if (session.completedTicks < session.durationTicks) {
                return null;
            }
            return new Completion(session.playerId, stopActive());
        } catch (RuntimeException | Error exception) {
            if (active != null) {
                stopActive();
            }
            throw exception;
        }
    }

    PerfReportData stop() {
        return active == null ? null : stopActive();
    }

    boolean cancelPending() {
        if (pending == null || active != null) {
            return false;
        }
        pending = null;
        return true;
    }

    boolean hasActiveSession() {
        return active != null;
    }

    boolean hasAnySession() {
        return pending != null || active != null;
    }

    PerfReportData currentReport() {
        return active == null ? lastReport : active.sampler.latestReport();
    }

    boolean clearLastReport() {
        if (hasAnySession()) {
            return false;
        }
        lastReport = null;
        return true;
    }

    void clear() {
        pending = null;
        if (active != null) {
            stopActive();
        } else {
            PerfWorkTracker.deactivate();
        }
        lastReport = null;
        nextSessionId = 1L;
    }

    private void activatePending() {
        PendingSession requested = pending;
        pending = null;
        PerfSamplerSession sampler = new PerfSamplerSession(nextSessionId++, TicketPerfConfig.sampleIntervalMicros());
        try {
            sampler.start();
            PerfWorkTracker.activate(Thread.currentThread());
            active = new ActiveSession(requested.durationTicks, requested.playerId, sampler);
        } catch (RuntimeException | Error exception) {
            PerfWorkTracker.deactivate();
            sampler.stop();
            throw exception;
        }
    }

    private PerfReportData stopActive() {
        ActiveSession session = active;
        active = null;
        PerfWorkTracker.deactivate();
        lastReport = session.sampler.stop();
        return lastReport;
    }

    private static long currentTickNanos(MinecraftServer server) {
        return ringValue(server.getTickTimesNanos(), server.getTickCount());
    }

    static long ringValue(long[] tickTimes, int tickCount) {
        return tickTimes[tickCount % tickTimes.length];
    }

    private void beginTick(MinecraftServer server) {
        ActiveSession session = active;
        if (session == null) {
            return;
        }
        session.sampler.beginTick(session.attribution);
        session.tickOpen = true;
        if (session.completedTicks % ATTRIBUTION_REFRESH_TICKS == 0) {
            session.attribution = PerfAttributionSnapshot.capture(server);
            session.sampler.updateAttribution(session.attribution);
        }
    }

    record Completion(UUID playerId, PerfReportData data) {
    }

    private record PendingSession(long durationTicks, UUID playerId) {
    }

    private static final class ActiveSession {
        final long durationTicks;
        final UUID playerId;
        final PerfSamplerSession sampler;
        PerfAttributionSnapshot attribution;
        long completedTicks;
        boolean tickOpen;

        ActiveSession(long durationTicks, UUID playerId, PerfSamplerSession sampler) {
            this.durationTicks = durationTicks;
            this.playerId = playerId;
            this.sampler = sampler;
        }
    }
}
