package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.weakload.ChunkEntityCollector;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PerfChunkEntityScanQueueTest {
    private static final WeakEntityType COW =
            new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");

    @Test
    void scansOnlyRedChunksAndPreservesOtherReportData() {
        var queue = new PerfChunkEntityScanQueue();
        PerfReportData.Metric red = new PerfReportData.Metric(2.0D, 2.0D, 10L);
        PerfReportData report = report(20L, List.of(
                chunk(1, 2, 7, 4, red),
                chunk(3, 4, 9, 5, new PerfReportData.Metric(1.0D, 1.0D, 8L))));
        List<PerfChunkEntityScanQueue.ChunkKey> scanned = new ArrayList<>();

        queue.accept(report);
        PerfChunkEntityScanQueue.ScanBatch batch = queue.scan((key, deletion) -> {
            scanned.add(key);
            return new PerfChunkEntityScanQueue.ScanValue(summary(key, 300), null);
        });

        assertEquals(1, batch.scannedChunks());
        assertEquals(1, scanned.size());
        assertEquals(1, batch.trackedCorrections().size());
        assertNull(batch.trackedCorrections().getFirst().entry());
        PerfReportData.ChunkEntry corrected = batch.reportUpdate().chunks().getFirst();
        assertEquals(300, corrected.entityCount());
        assertEquals(300, corrected.topEntities().getFirst().count());
        assertEquals(4, corrected.blockEntityCount());
        assertEquals(red, corrected.metric());
        assertEquals(9, batch.reportUpdate().chunks().get(1).entityCount());
    }

    @Test
    void deletionRefreshOverridesAndDeduplicatesAQueuedRedScan() {
        var queue = new PerfChunkEntityScanQueue();
        queue.accept(report(20L, List.of(chunk(1, 2, 7, 0,
                new PerfReportData.Metric(2.0D, 2.0D, 10L)))));
        long position = ChunkPos.asLong(1, 2);
        queue.prioritize(List.of(new WeakChunkClearJobs.CompletedChunk(Level.OVERWORLD, position)));
        List<Boolean> deletionFlags = new ArrayList<>();

        PerfChunkEntityScanQueue.ScanBatch batch = queue.scan((key, deletion) -> {
            deletionFlags.add(deletion);
            WeakChunkSnapshot.ChunkEntry tracked = new WeakChunkSnapshot.ChunkEntry(
                    1, 2, 200, Map.of(COW, 200));
            return new PerfChunkEntityScanQueue.ScanValue(summary(key, 250), tracked);
        });

        assertEquals(1, batch.scannedChunks());
        assertEquals(List.of(true), deletionFlags);
        assertEquals(1, batch.trackedCorrections().size());
        assertEquals(250, batch.reportUpdate().chunks().getFirst().entityCount());
    }

    @Test
    void aNewReportDropsStaleOrdinaryTasksButReusesCachedCounts() {
        var queue = new PerfChunkEntityScanQueue();
        PerfReportData.Metric red = new PerfReportData.Metric(2.0D, 2.0D, 10L);
        queue.accept(report(20L, List.of(chunk(1, 2, 7, 0, red))));
        queue.scan((key, deletion) ->
                new PerfChunkEntityScanQueue.ScanValue(summary(key, 300), null));

        PerfReportData cached = queue.accept(report(40L, List.of(chunk(1, 2, 8, 0, red))));
        queue.accept(report(60L, List.of(chunk(3, 4, 9, 0, red))));
        List<PerfChunkEntityScanQueue.ChunkKey> scanned = new ArrayList<>();
        queue.scan((key, deletion) -> {
            scanned.add(key);
            return new PerfChunkEntityScanQueue.ScanValue(summary(key, 400), null);
        });

        assertEquals(300, cached.chunks().getFirst().entityCount());
        assertEquals(1, scanned.size());
        assertEquals(ChunkPos.asLong(3, 4), scanned.getFirst().chunkPos());
    }

    @Test
    void redThresholdDoesNotIncludeAnOrangeChunk() {
        var queue = new PerfChunkEntityScanQueue();
        queue.accept(report(20L, List.of(chunk(1, 2, 300, 0,
                new PerfReportData.Metric(1.0D, 1.0D, 10L)))));

        PerfChunkEntityScanQueue.ScanBatch batch = queue.scan((key, deletion) -> {
            throw new AssertionError("Orange chunks must not be scanned");
        });

        assertEquals(0, batch.scannedChunks());
        assertNull(batch.reportUpdate());
    }

    @Test
    void drainsEveryRedChunkEvenWhenTheQueueExceedsTheBaseBudget() {
        var queue = new PerfChunkEntityScanQueue();
        PerfReportData.Metric red = new PerfReportData.Metric(2.0D, 2.0D, 10L);
        List<PerfReportData.ChunkEntry> chunks = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            chunks.add(chunk(index, 0, 200, 0, red));
        }
        queue.accept(report(20L, chunks));

        PerfChunkEntityScanQueue.ScanBatch batch = queue.scan((key, deletion) ->
                new PerfChunkEntityScanQueue.ScanValue(summary(key, 200), null));

        assertEquals(70, batch.scannedChunks());
        assertEquals(0, WeakChunkDelivery.remainingWeakBudget(batch.scannedChunks()));
    }

    private static PerfReportData report(long tickCount, List<PerfReportData.ChunkEntry> chunks) {
        return new PerfReportData(7L, 1.0D, 1_000L, tickCount * 50L, tickCount, 100L, 1_000,
                0, 0, 0, 0, chunks, List.of(), List.of(), List.of());
    }

    private static PerfReportData.ChunkEntry chunk(
            int x, int z, int entities, int blockEntities, PerfReportData.Metric metric) {
        var sampled = new PerfReportData.ObjectCount(
                "Cow", COW.id(), TickDetailKind.ENTITY, entities, x * 16, 64, z * 16);
        return new PerfReportData.ChunkEntry(Level.OVERWORLD.location().toString(), x, z,
                entities, blockEntities, List.of(sampled), List.of(), metric);
    }

    private static ChunkEntityCollector.ChunkEntitySummary summary(
            PerfChunkEntityScanQueue.ChunkKey key, int count) {
        ChunkPos position = new ChunkPos(key.chunkPos());
        var estimate = new ChunkEntityCollector.TypeEstimate(
                count, position.getMiddleBlockX(), 64, position.getMiddleBlockZ());
        return new ChunkEntityCollector.ChunkEntitySummary(
                position.x, position.z, count, Map.of(COW, estimate));
    }
}
