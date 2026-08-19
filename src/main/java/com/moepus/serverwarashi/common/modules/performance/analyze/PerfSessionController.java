package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfSamplerSession;
import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.function.IntSupplier;

final class PerfSessionController {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int TOPOLOGY_CHECK_TICKS = 5;

    private final IntSupplier sampleIntervalMicros;
    private ActiveSession active;
    private PerfReportData lastReport;
    private PerfAttributionSnapshot lastAttribution;
    private long nextSessionId = 1L;

    PerfSessionController() {
        this(TicketPerfConfig::sampleIntervalMicros);
    }

    PerfSessionController(IntSupplier sampleIntervalMicros) {
        this.sampleIntervalMicros = sampleIntervalMicros;
    }

    boolean start(MinecraftServer server, int durationSeconds, UUID playerId) {
        if (active != null) {
            return false;
        }
        boolean started = start(durationSeconds, playerId,
                PerfAttributionSnapshot.captureLoaderLayout(server));
        if (started) {
            active.attributionComplete = false;
        }
        return started;
    }

    boolean start(int durationSeconds, UUID playerId, PerfAttributionSnapshot attribution) {
        if (active != null) {
            return false;
        }
        long durationNanos = durationSeconds * NANOS_PER_SECOND;
        PerfSamplerSession sampler = PerfSamplerSession.create(
                nextSessionId++, sampleIntervalMicros.getAsInt(),
                new PerfSamplerSession.Options(attribution, durationNanos));
        try {
            active = new ActiveSession(playerId, sampler, attribution);
            return true;
        } catch (RuntimeException | Error exception) {
            PerfWorkTracker.deactivate();
            sampler.stop();
            throw exception;
        }
    }

    void onServerTickPre(MinecraftServer server) {
        PerfWorkTracker.clearCurrent();
        try {
            beginTick(server);
        } catch (RuntimeException | Error exception) {
            if (active != null) {
                stopActive();
            }
            throw exception;
        }
    }

    Completion onServerPaused() {
        ActiveSession session = active;
        if (session == null || !session.samplerStarted) {
            return null;
        }
        if (session.sampler.pause()) {
            PerfWorkTracker.deactivate();
        }
        if (!session.sampler.hasReachedDuration()) {
            return null;
        }
        return new Completion(session.playerId, stopActive());
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
            if (!session.sampler.hasReachedDuration()) {
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
        return stop(false);
    }

    PerfReportData stop(boolean retainInitialLayout) {
        return active == null ? null : stopActive(retainInitialLayout);
    }

    boolean hasActiveSession() {
        return active != null;
    }

    PerfReportData currentReport() {
        return active == null ? lastReport : active.sampler.latestReport();
    }

    PerfAttributionSnapshot currentAttribution() {
        return active == null ? lastAttribution : active.attribution;
    }

    boolean clearLastReport() {
        if (hasActiveSession()) {
            return false;
        }
        lastReport = null;
        lastAttribution = null;
        return true;
    }

    void clear() {
        if (active != null) {
            stopActive();
        } else {
            PerfWorkTracker.deactivate();
        }
        lastReport = null;
        lastAttribution = null;
        nextSessionId = 1L;
    }

    private PerfReportData stopActive() {
        return stopActive(false);
    }

    private PerfReportData stopActive(boolean retainInitialLayout) {
        ActiveSession session = active;
        active = null;
        PerfWorkTracker.deactivate();
        lastReport = session.sampler.stop(retainInitialLayout);
        lastAttribution = lastReport.reportId() == 0L ? null : session.attribution;
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
        if (!session.attributionComplete) {
            PerfAttributionSnapshot.Capture capture = PerfAttributionSnapshot.captureWithFingerprint(server);
            session.attribution = capture.snapshot();
            session.ticketFingerprint = capture.ticketFingerprint();
            session.attributionComplete = true;
        }
        if (!startOrResumeSampling(session)) {
            session.tickOpen = true;
            return;
        }
        session.sampler.beginTick(session.attribution);
        session.tickOpen = true;
        if (session.completedTicks > 0
                && session.completedTicks % TOPOLOGY_CHECK_TICKS == 0) {
            long topologyFingerprint = PerfAttributionSnapshot.ticketFingerprint(server);
            boolean topologyChanged = topologyFingerprint != session.ticketFingerprint;
            if (!topologyChanged && session.attribution != null) {
                return;
            }
            PerfAttributionSnapshot next = PerfAttributionSnapshot.capture(server);
            boolean loaderLayoutChanged = !sameLoaderLayout(session.attribution, next);
            session.attribution = next;
            session.ticketFingerprint = topologyFingerprint;
            session.sampler.updateAttribution(next, loaderLayoutChanged);
        }
    }

    private static boolean startOrResumeSampling(ActiveSession session) {
        if (!session.samplerStarted) {
            session.sampler.start();
            session.samplerStarted = true;
            PerfWorkTracker.activate(Thread.currentThread());
            return true;
        }
        boolean resumed = session.sampler.resume();
        if (session.sampler.hasReachedDuration()) {
            PerfWorkTracker.deactivate();
            return false;
        }
        if (resumed) {
            PerfWorkTracker.activate(Thread.currentThread());
        }
        return true;
    }

    private static boolean sameLoaderLayout(PerfAttributionSnapshot previous,
                                            PerfAttributionSnapshot next) {
        if (previous == null) {
            return false;
        }
        return previous.reportLoaderPlan().equals(next.reportLoaderPlan());
    }

    record Completion(UUID playerId, PerfReportData data) {
    }

    private static final class ActiveSession {
        final UUID playerId;
        final PerfSamplerSession sampler;
        PerfAttributionSnapshot attribution;
        long ticketFingerprint = Long.MIN_VALUE;
        long completedTicks;
        boolean attributionComplete = true;
        boolean samplerStarted;
        boolean tickOpen;

        ActiveSession(UUID playerId,
                      PerfSamplerSession sampler,
                      PerfAttributionSnapshot attribution) {
            this.playerId = playerId;
            this.sampler = sampler;
            this.attribution = attribution;
        }
    }

}
