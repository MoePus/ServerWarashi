package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSamplerSessionTest {
    @Test
    void rejectsNonPositiveSampleIntervalBeforeStartingThread() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerfSamplerSession(1L, 0, null));
    }

    @Test
    void publishesLoaderLayoutBeforeFirstTick() {
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));

        PerfSamplerSession session = new PerfSamplerSession(1L, 1_000, attribution);
        var report = session.latestReport();

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
        PerfSamplerSession session = new PerfSamplerSession(2L, 1_000, attribution);
        session.start();

        var report = session.stop();

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
        PerfSamplerSession session = new PerfSamplerSession(4L, 1_000, attribution);
        session.start();

        var report = session.stop(true);

        assertEquals(0L, report.tickCount());
        assertEquals(1, report.loaderSources().size());
        assertEquals("source", report.loaderSources().getFirst().sourceId());
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
    void initialSamplePhaseSpansTheWholeInterval() {
        assertEquals(0L, PerfSamplerSession.initialDelay(1_000L, 0L));
        assertEquals(999L, PerfSamplerSession.initialDelay(1_000L, -1L));
    }

    @Test
    void publishesSnapshotAfterTwentyClosedTicks() throws InterruptedException {
        PerfSamplerSession session = new PerfSamplerSession(1L, 1_000, null);
        session.start();
        try {
            for (int tick = 0; tick < 20; tick++) {
                session.beginTick(null);
                session.endTick(1_000_000L);
            }
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (session.latestReport().tickCount() < 20 && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }
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
        PerfSamplerSession session = new PerfSamplerSession(3L, 1_000, null);
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
}
