package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSamplerSessionTest {
    private static final long CADENCE_WINDOW_MILLIS = 500L;
    private static final long PAUSE_WINDOW_MILLIS = 100L;
    private static final long MAX_SAMPLER_CPU_NANOS = 100_000_000L;
    private static final double MIN_ATTEMPTS_PER_SECOND = 700.0D;
    private static final double MAX_ATTEMPTS_PER_SECOND = 1_300.0D;

    @Test
    void attachesActiveBetweenTickFallbackSamplesToTheNextTick() throws InterruptedException {
        PerfSamplerSession session = unlimitedSession(12L, null);
        boolean stopped = false;
        PerfWorkTracker.activate(Thread.currentThread());
        session.start();
        try {
            Thread.sleep(50L);
            session.beginTick(null);
            session.endTick(1_000_000L);

            PerfReportData report = session.stop();
            stopped = true;
            assertTrue(report.tickables().stream().anyMatch(entry ->
                    entry.type() == TickType.TASK && "Unknown".equals(entry.className())));
        } finally {
            PerfWorkTracker.deactivate();
            if (!stopped) {
                session.stop();
            }
        }
    }

    @Test
    void samplesOffTickWallTimeAndStopsCompletelyWhilePaused() throws Exception {
        PerfSamplerSession session = unlimitedSession(9L, null);
        boolean stopped = false;
        session.start();
        try {
            Thread sampler = samplerThread(session);
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long cpuBefore = threadCpuNanos(threadMXBean, sampler);
            Thread.sleep(CADENCE_WINDOW_MILLIS);
            session.beginTick(null);
            session.endTick(1_000_000L);
            assertTrue(session.pause());
            long cpuAfter = threadCpuNanos(threadMXBean, sampler);

            PerfReportData paused = session.latestReport();
            double attemptsPerSecond = paused.totalSamples() * 1_000.0D / paused.elapsedMillis();
            assertTrue(attemptsPerSecond >= MIN_ATTEMPTS_PER_SECOND, attemptsPerSecond + " attempts/s");
            assertTrue(attemptsPerSecond <= MAX_ATTEMPTS_PER_SECOND, attemptsPerSecond + " attempts/s");
            if (cpuBefore >= 0L && cpuAfter >= 0L) {
                assertTrue(cpuAfter - cpuBefore < MAX_SAMPLER_CPU_NANOS,
                        (cpuAfter - cpuBefore) + "ns sampler CPU");
            }

            Thread.sleep(PAUSE_WINDOW_MILLIS);
            PerfReportData finished = session.stop();
            stopped = true;
            assertEquals(paused.totalSamples(), finished.totalSamples());
            assertEquals(paused.elapsedMillis(), finished.elapsedMillis());
        } finally {
            if (!stopped) {
                session.stop();
            }
        }
    }

    @Test
    void activeDeadlineCapsAPathologicallySlowServerTick() {
        AtomicLong now = new AtomicLong();
        PerfSamplerSession session = PerfSamplerSession.create(7L, 1_000,
                new PerfSamplerSession.Options(null, 5_000_000L, now::get));
        session.start();
        session.beginTick(null);

        now.set(30_000_000_000L);
        assertTrue(session.hasReachedDuration());
        session.endTick(30_000_000_000L);

        PerfReportData report = session.stop();
        assertEquals(5L, report.elapsedMillis());
    }

    @Test
    void reportElapsedTimeExcludesPausedIntegratedServerTime() {
        AtomicLong now = new AtomicLong();
        PerfSamplerSession session = PerfSamplerSession.create(6L, 1_000,
                new PerfSamplerSession.Options(null, Long.MAX_VALUE, now::get));
        session.start();
        session.beginTick(null);
        session.endTick(1_000_000L);

        now.set(3_000_000L);
        assertTrue(session.pause());
        now.set(23_000_000L);
        assertTrue(session.resume());
        now.set(25_000_000L);

        PerfReportData report = session.stop();
        assertEquals(5L, report.elapsedMillis());
    }

    @Test
    void pausePublishesTheLastPartialReportWindow() {
        PerfSamplerSession session = unlimitedSession(8L, null);
        session.start();
        try {
            session.beginTick(null);
            session.endTick(1_000_000L);
            assertTrue(session.pause());
            assertEquals(1L, session.latestReport().tickCount());
        } finally {
            session.stop();
        }
    }

    @Test
    void refreshRequestStaysWithItsTickWhenClosedTicksBackUp() throws Exception {
        PerfAttributionSnapshot oldAttribution = loaderAttribution("old");
        PerfAttributionSnapshot nextAttribution = loaderAttribution("next");
        PerfSamplerSession session = unlimitedSession(10L, oldAttribution);
        Object aggregationLock = aggregationLock(session);

        try {
            synchronized (aggregationLock) {
                session.start();
                session.beginTick(oldAttribution);
                session.endTick(1_000_000L);
                session.beginTick(oldAttribution);
                session.updateAttribution(nextAttribution, true);
                session.endTick(1_000_000L);
            }

            awaitTickCount(session, 2L);
            PerfReportData report = session.latestReport();
            assertEquals(2L, report.tickCount());
            assertEquals("next", report.loaderSources().getFirst().sourceId());
        } finally {
            session.stop();
        }
    }

    @Test
    void rejectsNonPositiveSampleIntervalBeforeStartingThread() {
        assertThrows(IllegalArgumentException.class,
                () -> PerfSamplerSession.create(
                        1L, 0, PerfSamplerSession.Options.unlimited(null)));
    }

    @Test
    void publishesLoaderLayoutBeforeFirstTick() {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));

        PerfSamplerSession session = unlimitedSession(1L, attribution);
        var report = session.latestReport();

        assertEquals(1L, report.reportId());
        assertEquals(0L, report.tickCount());
        assertEquals(0L, report.totalSamples());
        assertEquals(0.0D, report.serverMspt());
        assertEquals(List.of(), report.chunks());
        assertEquals(List.of(), report.tickables());
        assertEquals(List.of(), report.owners());
        assertEquals(1, report.loaderSources().size());
        assertEquals("source", report.loaderSources().getFirst().sourceId());
    }

    @Test
    void stopBeforeFirstTickDiscardsLoaderOnlyReport() {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));
        PerfSamplerSession session = unlimitedSession(2L, attribution);
        session.start();

        var report = session.stop();

        assertEquals(0L, report.reportId());
        assertEquals(0L, report.tickCount());
        assertEquals(0, report.loaderSources().size());
    }

    @Test
    void pausedStopBeforeFirstTickCanKeepLoaderLayout() {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));
        PerfSamplerSession session = unlimitedSession(4L, attribution);
        session.start();

        var report = session.stop(true);

        assertEquals(0L, report.tickCount());
        assertEquals(1, report.loaderSources().size());
        assertEquals("source", report.loaderSources().getFirst().sourceId());
    }

    @Test
    void stopBeforeStartDoesNotWaitForTheSamplerThread() {
        PerfReportData report = unlimitedSession(5L, null).stop();

        assertEquals(0L, report.tickCount());
        assertEquals(0L, report.totalSamples());
    }

    @Test
    void jitterCanUseMinimumHalfInterval() {
        assertEquals(500L, PerfSamplerSession.jitteredDelay(1_000L, 0L));
    }

    @Test
    void jitterStaysBelowOneAndAHalfIntervals() {
        assertEquals(1_499L, PerfSamplerSession.jitteredDelay(1_000L, -1L));
    }

    @Test
    void publishesSnapshotAfterTwentyClosedTicks() throws InterruptedException {
        PerfSamplerSession session = unlimitedSession(1L, null);
        session.start();
        try {
            for (int tick = 0; tick < 20; tick++) {
                session.beginTick(null);
                session.endTick(1_000_000L);
            }
            awaitTickCount(session, 20L);
            assertEquals(20L, session.latestReport().tickCount());
        } finally {
            session.stop();
        }
    }

    @Test
    void publishesFirstUsefulSnapshotBeforeThePeriodicBoundary() {
        assertTrue(PerfSamplerSession.shouldPublishReport(1L, false, true));
        assertFalse(PerfSamplerSession.shouldPublishReport(1L, false, false));
        assertTrue(PerfSamplerSession.shouldPublishReport(20L, false, false));
        assertTrue(PerfSamplerSession.shouldPublishReport(7L, true, false));
    }

    @Test
    void doesNotPublishAnEmptyFirstTickAsUsefulData() throws InterruptedException {
        PerfSamplerSession session = unlimitedSession(3L, null);
        session.start();
        try {
            session.beginTick(null);
            session.endTick(1_000_000L);
            Thread.sleep(50L);
            assertEquals(0L, session.latestReport().tickCount());
        } finally {
            session.stop();
        }
    }

    private static Thread samplerThread(PerfSamplerSession session) throws ReflectiveOperationException {
        Field field = PerfSamplerSession.class.getDeclaredField("samplerThread");
        field.setAccessible(true);
        return (Thread) field.get(session);
    }

    private static Object aggregationLock(PerfSamplerSession session) throws ReflectiveOperationException {
        Field field = PerfSamplerSession.class.getDeclaredField("aggregationLock");
        field.setAccessible(true);
        return field.get(session);
    }

    private static void awaitTickCount(PerfSamplerSession session, long expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (session.latestReport().tickCount() < expected && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
    }

    private static long threadCpuNanos(ThreadMXBean threadMXBean, Thread thread) {
        if (!threadMXBean.isThreadCpuTimeSupported()) {
            return -1L;
        }
        if (!threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }
        return threadMXBean.getThreadCpuTime(thread.threadId());
    }

    private static PerfSamplerSession unlimitedSession(
            long sessionId, PerfAttributionSnapshot attribution) {
        return PerfSamplerSession.create(
                sessionId, 1_000, PerfSamplerSession.Options.unlimited(attribution));
    }

    private static PerfAttributionSnapshot loaderAttribution(String sourceId) {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader-" + sourceId,
                "Loader " + sourceId, false, 0L, null);
        var source = new PerfAttributionSnapshot.Source(sourceId, "Loader " + sourceId, null);
        return PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));
    }
}
