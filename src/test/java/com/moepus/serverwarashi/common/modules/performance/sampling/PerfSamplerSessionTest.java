package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSamplerSessionTest {
    private static final long PAUSE_WINDOW_MILLIS = 100L;

    @Test
    void recordsTickContextGapsAsUnknownWithoutJfrDetails() throws InterruptedException {
        FakeAsyncStackSampler sampler = new FakeAsyncStackSampler();
        PerfSamplerSession session = unlimitedSession(12L, null, sampler);
        boolean stopped = false;
        PerfWorkTracker.activate(Thread.currentThread());
        session.start();
        try {
            session.beginTick(null);
            Thread.sleep(30L);
            session.endTick(1_000_000L);

            PerfReportData report = session.stop();
            stopped = true;
            assertTrue(report.tickables().stream().anyMatch(entry ->
                    entry.type() == TickType.TASK && "Unknown".equals(entry.className())));
            PerfReportData.TickableEntry unknown = report.tickables().stream()
                    .filter(entry -> "Unknown".equals(entry.className())).findFirst().orElseThrow();
            assertEquals(PerfReportData.AttributionSource.FALLBACK_UNKNOWN,
                    unknown.attributionSource());
            assertEquals(PerfReportData.Confidence.LOW, unknown.confidence());
            assertTrue(unknown.metric().samples() > 0L);
            assertEquals(0L, session.unknownStackSnapshot().totalObserved());
        } finally {
            PerfWorkTracker.deactivate();
            if (!stopped) {
                session.stop();
            }
        }
    }

    @Test
    void stopsBackendAndFreezesMeasurementWhilePaused() throws Exception {
        FakeAsyncStackSampler sampler = new FakeAsyncStackSampler();
        PerfSamplerSession session = unlimitedSession(9L, null, sampler);
        boolean stopped = false;
        session.start();
        try {
            session.beginTick(null);
            sampler.emit(frame("com.example.Work", "run"));
            session.awaitWorkerDrain();
            session.endTick(1_000_000L);
            assertTrue(session.pause());

            PerfReportData paused = session.latestReport();
            assertEquals(1, sampler.starts);
            assertEquals(1, sampler.stops);

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
        PerfSamplerSession session = testSession(7L, 1_000,
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
        PerfSamplerSession session = testSession(6L, 1_000,
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
    void refreshRequestStaysWithItsTickWhenClosedTicksBackUp() {
        PerfAttributionSnapshot oldAttribution = loaderAttribution("old");
        PerfAttributionSnapshot nextAttribution = loaderAttribution("next");
        PerfSamplerSession session = unlimitedSession(10L, oldAttribution);
        try {
            session.start();
            session.beginTick(oldAttribution);
            session.endTick(1_000_000L);
            session.beginTick(oldAttribution);
            session.updateAttribution(nextAttribution, true);
            session.endTick(1_000_000L);
            assertTrue(session.pause());
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
        assertThrows(IllegalArgumentException.class,
                () -> PerfSamplerSession.create(
                        1L, 999, PerfSamplerSession.Options.unlimited(null)));
        assertThrows(IllegalArgumentException.class,
                () -> PerfSamplerSession.create(
                        1L, 100_001, PerfSamplerSession.Options.unlimited(null)));
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
    void stopExcludesUnassignedBetweenTickUnknownStacks() throws InterruptedException {
        PerfSamplerSession session = unlimitedSession(13L, null);
        PerfWorkTracker.activate(Thread.currentThread());
        session.start();
        try {
            Thread.sleep(30L);
        } finally {
            PerfWorkTracker.deactivate();
        }

        session.stop();

        assertEquals(0L, session.unknownStackSnapshot().totalObserved());
        assertEquals(0, session.unknownStackSnapshot().retainedCount());
    }

    @Test
    void boundedQueueReportsBackpressureDrops() throws Exception {
        FakeAsyncStackSampler sampler = new FakeAsyncStackSampler();
        PerfSamplerSession session = unlimitedSession(14L, null, sampler);
        session.start();
        session.beginTick(null);
        StackTraceElement[] stack = {frame("com.example.QueuedWork", "run")};
        synchronized (stateLock(session)) {
            for (int sample = 0; sample < 20_000; sample++) {
                sampler.emit(stack);
            }
        }
        session.awaitWorkerDrain();
        session.endTick(1_000_000L);

        PerfReportData report = session.stop();

        assertTrue(report.sampling().droppedSamples() > 0L);
        assertTrue(report.sampling().degradationReason().contains("backpressure"));
        assertTrue(report.totalSamples() < 1_000L,
                "JFR detail events must not become primary samples");
    }

    @Test
    void detailStartupFailureDoesNotStopPrimaryUnknownTiming()
            throws InterruptedException {
        PerfSamplerSession session = unlimitedSession(
                17L, null, new FailingAsyncStackSampler());
        session.start();
        session.beginTick(null);
        Thread.sleep(30L);
        session.endTick(1_000_000L);

        PerfReportData report = session.stop();

        assertTrue(report.totalSamples() > 0L);
        assertTrue(report.tickables().stream().anyMatch(entry ->
                entry.type() == TickType.TASK && "Unknown".equals(entry.className())));
        assertTrue(report.sampling().degradationReason().contains("startup failed"));
    }

    @Test
    void detailRuntimeFailureDoesNotStopPrimaryHandlerTiming()
            throws InterruptedException {
        RuntimeFailureSampler sampler = new RuntimeFailureSampler();
        PerfSamplerSession session = unlimitedSession(18L, null, sampler);
        PerfWorkTracker.activate(Thread.currentThread());
        session.start();
        try {
            session.beginTick(null);
            assertTrue(PerfWorkTracker.enterHandler());
            sampler.fail();
            Thread.sleep(30L);
            assertTrue(PerfWorkTracker.exit(TickType.HANDLER));
            session.endTick(1_000_000L);

            PerfReportData report = session.stop();
            assertTrue(report.tickables().stream().anyMatch(entry ->
                    entry.type() == TickType.HANDLER && "Handler".equals(entry.className())));
            assertTrue(report.sampling().degradationReason().contains("runtime failure"));
        } finally {
            PerfWorkTracker.deactivate();
            session.stop();
        }
    }

    @Test
    void jfrRefinesActiveHandlerNameWithoutChangingItsType()
            throws InterruptedException {
        FakeAsyncStackSampler sampler = new FakeAsyncStackSampler();
        PerfSamplerSession session = unlimitedSession(15L, null, sampler);
        PerfWorkTracker.activate(Thread.currentThread());
        session.start();
        try {
            session.beginTick(null);
            assertTrue(PerfWorkTracker.enterHandler());
            Thread.sleep(30L);
            long detailTimestamp = System.nanoTime() - 10_000_000L;
            sampler.emitAt(detailTimestamp, handlerStack());
            sampler.emitAt(detailTimestamp, handlerStack());
            session.awaitWorkerDrain();
            assertTrue(PerfWorkTracker.exit(TickType.HANDLER));
            session.endTick(1_000_000L);

            PerfReportData.TickableEntry entry = session.stop().tickables().stream()
                    .filter(candidate -> candidate.type() == TickType.HANDLER)
                    .findFirst().orElseThrow();
            assertEquals("ServerEvents#onTick(ServerTickEvent.Pre)", entry.className());
            assertEquals(PerfReportData.AttributionSource.ASYNC_STACK,
                    entry.attributionSource());
            assertEquals(PerfReportData.Confidence.MEDIUM, entry.confidence());
        } finally {
            PerfWorkTracker.deactivate();
            session.stop();
        }
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

    private static Object stateLock(PerfSamplerSession session) throws ReflectiveOperationException {
        Field field = PerfSamplerSession.class.getDeclaredField("stateLock");
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

    private static PerfSamplerSession unlimitedSession(
            long sessionId, PerfAttributionSnapshot attribution) {
        return unlimitedSession(sessionId, attribution, new FakeAsyncStackSampler());
    }

    private static PerfSamplerSession unlimitedSession(
            long sessionId, PerfAttributionSnapshot attribution, AsyncStackSampler sampler) {
        return PerfSamplerSession.createForTest(
                sessionId, 1_000, PerfSamplerSession.Options.unlimited(attribution), sampler);
    }

    private static PerfSamplerSession testSession(long sessionId, int intervalMicros,
                                                  PerfSamplerSession.Options options) {
        return PerfSamplerSession.createForTest(
                sessionId, intervalMicros, options, new FakeAsyncStackSampler());
    }

    private static StackTraceElement frame(String className, String methodName) {
        return new StackTraceElement(className, methodName, "Test.java", 1);
    }

    private static StackTraceElement[] handlerStack() {
        return new StackTraceElement[]{
                frame("com.example.ServerEvents", "onTick"),
                frame("net.neoforged.bus.ConsumerEventHandler", "invoke"),
                frame("net.neoforged.bus.EventBus", "post"),
                frame("net.neoforged.neoforge.event.EventHooks", "fireServerTickPre")
        };
    }

    private static PerfAttributionSnapshot loaderAttribution(String sourceId) {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader-" + sourceId,
                "Loader " + sourceId, false, 0L, null);
        var source = new PerfAttributionSnapshot.Source(sourceId, "Loader " + sourceId, null);
        return PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));
    }

    private static class FakeAsyncStackSampler implements AsyncStackSampler {
        Consumer<AsyncStackSample> sink;
        boolean running;
        int starts;
        int stops;

        @Override
        public void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sink) {
            this.sink = sink;
            running = true;
            starts++;
        }

        @Override
        public void stop() {
            running = false;
            stops++;
        }

        @Override
        public String backendName() {
            return "Test async sampler";
        }

        @Override
        public String degradationReason() {
            return "";
        }

        @Override
        public long settlingNanos() {
            return 0L;
        }

        void emit(StackTraceElement... stack) {
            emitAt(System.nanoTime(), stack);
        }

        void emitAt(long timestampNanos, StackTraceElement... stack) {
            if (running) {
                sink.accept(new AsyncStackSample(timestampNanos, stack));
            }
        }
    }

    private static final class FailingAsyncStackSampler implements AsyncStackSampler {
        @Override
        public void start(Thread target, int intervalMicros, Consumer<AsyncStackSample> sink) {
            throw new IllegalStateException("Unavailable for test");
        }

        @Override
        public void stop() {
        }

        @Override
        public String backendName() {
            return "Failing test sampler";
        }

        @Override
        public String degradationReason() {
            return "";
        }

        @Override
        public long settlingNanos() {
            return 0L;
        }
    }

    private static final class RuntimeFailureSampler extends FakeAsyncStackSampler {
        private String failure = "";

        void fail() {
            failure = "JFR detail runtime failure";
        }

        @Override
        public String degradationReason() {
            return failure;
        }
    }
}
