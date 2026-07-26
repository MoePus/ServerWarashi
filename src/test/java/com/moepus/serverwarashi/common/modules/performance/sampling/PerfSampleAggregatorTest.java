package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSampleAggregatorTest {
    @Test
    void splitsPositionCostEquallyAcrossAttributionBuckets() {
        assertEquals(4.0D, PerfOwnerAggregator.equalShare(12.0D, 3), 0.0D);
        assertEquals(1.5D, PerfOwnerAggregator.equalShare(3.0D, 2), 0.0D);
        assertThrows(IllegalArgumentException.class, () -> PerfOwnerAggregator.equalShare(1.0D, 0));
    }

    @Test
    void usesNeoForgeDurationAndIncludesZeroSampleTicks() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(1L);
        TickSamples sampled = new TickSamples(null);
        sampled.sample(new PerfWorkTracker.Snapshot(TickType.TASK, null, Runnable.class, null,
                TickDetailKind.NONE, -1, false, 0, 0, 0, false, "server"));
        sampled.durationNanos = 20_000_000L;
        aggregator.accept(sampled);

        TickSamples empty = new TickSamples(null);
        empty.durationNanos = 40_000_000L;
        aggregator.accept(empty);

        var report = aggregator.build(100L, 1_000);
        assertEquals(30.0D, report.serverMspt(), 0.000_001D);
        assertEquals(2L, report.tickCount());
        assertEquals(1L, report.totalSamples());
        assertEquals(10.0D, report.tickables().getFirst().metric().mspt(), 0.000_001D);
        assertTrue(report.chunks().isEmpty());
    }

    @Test
    void reportsDistinctEntityAndBlockEntityCountsForChunk() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(2L);
        TickSamples sampled = new TickSamples(null);
        sampled.sample(snapshot(new TestWork(TickType.ENTITY, Cow.class, 1), new BlockPos(33, 64, -17)));
        sampled.sample(snapshot(new TestWork(TickType.ENTITY, Cow.class, 2), new BlockPos(34, 64, -18)));
        sampled.sample(snapshot(new TestWork(TickType.BLOCK_ENTITY, Furnace.class, -1),
                new BlockPos(35, 63, -19)));
        sampled.durationNanos = 30_000_000L;
        aggregator.accept(sampled);
        for (int tick = 1; tick < 100; tick++) {
            TickSamples empty = new TickSamples(null);
            empty.durationNanos = 1_000_000L;
            aggregator.accept(empty);
        }

        var report = aggregator.build(50L, 1_000);
        var chunk = report.chunks().getFirst();
        assertEquals(2, chunk.entityCount());
        assertEquals(1, chunk.blockEntityCount());
        assertEquals("Cow", chunk.topEntities().getFirst().className());
        assertEquals(2, chunk.topEntities().getFirst().count());
        assertEquals("Furnace", chunk.topBlockEntities().getFirst().className());
        assertEquals(1, chunk.topBlockEntities().getFirst().count());
        assertEquals(30.0D, chunk.metric().p98(), 0.000_001D);
        assertEquals(10.0D, report.tickables().getFirst().metric().p98(), 0.000_001D);
    }

    @Test
    void capsCoordinatesPerObjectGroupWithoutReducingChunkCounts() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(5L);
        TickSamples sampled = new TickSamples(null);
        for (int id = 0; id < 40; id++) {
            sampled.sample(snapshot(new TestWork(TickType.ENTITY, Cow.class, id),
                    new BlockPos(id % 16, 64, id / 16)));
        }
        sampled.durationNanos = 40_000_000L;
        aggregator.accept(sampled);

        var report = aggregator.build(50L, 1_000);
        long cowCoordinates = report.tickables().stream()
                .filter(entry -> entry.type() == TickType.ENTITY && entry.className().equals("Cow"))
                .count();

        assertEquals(16, cowCoordinates);
        assertEquals(40, report.chunks().getFirst().entityCount());
        assertEquals(24, report.omittedTickables());
    }

    @Test
    void capsOwnerPayloadAtCodecLimit() {
        Map<Long, List<PerfAttributionSnapshot.Bucket>> chunks = new HashMap<>();
        for (int index = 0; index < 4_200; index++) {
            var owner = new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.LOADER,
                    "loader-" + index, "Loader " + index, false, 0L, null);
            var source = new PerfAttributionSnapshot.Source("source-" + index, "Loader " + index, null);
            chunks.put(ChunkPos.asLong(index, 0), List.of(
                    new PerfAttributionSnapshot.Bucket(owner, source, 0, 0)));
        }
        PerfOwnerAggregator aggregator = new PerfOwnerAggregator();
        aggregator.updateMetadata(PerfAttributionSnapshot.create("test:level", chunks));

        PerfOwnerAggregator.Build build = aggregator.build(1L);

        assertTrue(build.entries().size() <= PerfReportLimits.MAX_ENTRIES);
        assertEquals(PerfReportLimits.MAX_LOADER_SOURCES, build.loaderSources().size());
        assertEquals(104, build.omittedLoaderSources());
        assertTrue(build.omitted() >= 104);
    }

    @Test
    void reportsItemDetailAndCapsEachChunkObjectGroupAtThree() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(3L);
        TickSamples sampled = new TickSamples(null);
        sampleEntities(sampled, Cow.class, 1, 3);
        PerfWorkTracker.Snapshot popularCow = snapshot(
                new TestWork(TickType.ENTITY, Cow.class, 1), new BlockPos(12, 65, 12));
        sampled.sample(popularCow);
        sampled.sample(popularCow);
        sampled.sample(popularCow);
        sampleEntities(sampled, Pig.class, 10, 2);
        sampleEntities(sampled, Sheep.class, 20, 1);
        sampleEntities(sampled, Zombie.class, 30, 1);
        sampleBlockEntities(sampled, Furnace.class, 1, 3);
        sampleBlockEntities(sampled, Chest.class, 5, 2);
        sampleBlockEntities(sampled, Hopper.class, 8, 1);
        sampleBlockEntities(sampled, Spawner.class, 9, 1);
        sampled.sample(new PerfWorkTracker.Snapshot(TickType.ENTITY, ResourceLocation.parse("test:level"),
                ItemEntityMarker.class, ResourceLocation.parse("minecraft:paper"),
                TickDetailKind.ITEM, 99, true, 8, 64, 8, false, null));
        sampled.durationNanos = 1_000_000L;
        aggregator.accept(sampled);

        var report = aggregator.build(50L, 1_000);
        var chunk = report.chunks().getFirst();
        assertEquals(List.of("Cow", "Pig", "ItemEntityMarker"),
                chunk.topEntities().stream().map(PerfReportData.ObjectCount::className).toList());
        assertEquals(new BlockPos(12, 65, 12), new BlockPos(chunk.topEntities().getFirst().x(),
                chunk.topEntities().getFirst().y(), chunk.topEntities().getFirst().z()));
        assertEquals(List.of("Furnace", "Chest", "Hopper"),
                chunk.topBlockEntities().stream().map(PerfReportData.ObjectCount::className).toList());
        assertEquals("minecraft:paper", report.tickables().stream()
                .filter(entry -> entry.className().equals("ItemEntityMarker"))
                .findFirst().orElseThrow().detailId());
        assertEquals("minecraft:paper", chunk.topEntities().get(2).detailId());
    }

    @Test
    void preservesTypedResourceIdsInTickableAndChunkReports() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(6L);
        TickSamples sampled = new TickSamples(null);
        sampled.sample(new PerfWorkTracker.Snapshot(TickType.BLOCK_ENTITY,
                ResourceLocation.parse("test:level"), Furnace.class,
                ResourceLocation.parse("minecraft:furnace"), TickDetailKind.BLOCK, -1,
                true, 8, 64, 8, false, null));
        sampled.sample(new PerfWorkTracker.Snapshot(TickType.ENTITY,
                ResourceLocation.parse("test:level"), Cat.class,
                ResourceLocation.parse("minecraft:cat"), TickDetailKind.ENTITY, 1,
                true, 9, 64, 9, false, null));
        sampled.durationNanos = 1_000_000L;
        aggregator.accept(sampled);

        var report = aggregator.build(50L, 1_000);
        var entity = report.tickables().stream()
                .filter(entry -> entry.type() == TickType.ENTITY).findFirst().orElseThrow();

        assertEquals("minecraft:cat", entity.detailId());
        assertEquals(TickDetailKind.ENTITY, entity.detailKind());
        assertEquals("minecraft:cat", report.chunks().getFirst().topEntities().getFirst().detailId());
        assertEquals("minecraft:furnace", report.chunks().getFirst()
                .topBlockEntities().getFirst().detailId());
    }

    @Test
    void aggregatesPlayerCostBySelfAndLoaderWithTicketChunkCounts() {
        var position = new PerfAttributionSnapshot.Position("test:level", 4, 65, 6);
        var owner = new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.PLAYER,
                "player", "Alice", true, 0L, position);
        var self = new PerfAttributionSnapshot.Bucket(owner,
                new PerfAttributionSnapshot.Source("self", "Self", null), 0, 0);
        var loader = new PerfAttributionSnapshot.Bucket(owner,
                new PerfAttributionSnapshot.Source("loader", "LoaderA",
                        new PerfAttributionSnapshot.Position("test:level", 8, 64, 9)), 0, 0);
        var attribution = PerfAttributionSnapshot.create("test:level", Map.of(
                ChunkPos.asLong(0, 0), List.of(self, loader),
                ChunkPos.asLong(1, 0), List.of(self)));
        TickSamples tick = new TickSamples(attribution);
        tick.sample(snapshot(new TestWork(TickType.ENTITY, Cow.class, 1), new BlockPos(8, 64, 8)));
        tick.durationNanos = 20_000_000L;

        PerfSampleAggregator aggregator = new PerfSampleAggregator(4L);
        aggregator.accept(tick);
        PerfReportData report = aggregator.build(50L, 1_000);
        PerfReportData.OwnerEntry result = report.owners().getFirst();
        Map<String, PerfReportData.OwnerSourceEntry> sources = result.sources().stream()
                .collect(java.util.stream.Collectors.toMap(PerfReportData.OwnerSourceEntry::name, entry -> entry));

        assertEquals(2, result.chunkCount());
        assertEquals(20.0D, result.metric().mspt(), 0.000_001D);
        assertEquals(2, sources.get("Self").chunkCount());
        assertEquals(1, sources.get("LoaderA").chunkCount());
        assertEquals(10.0D, sources.get("Self").metric().mspt(), 0.000_001D);
        assertEquals(10.0D, sources.get("LoaderA").metric().mspt(), 0.000_001D);
        assertEquals(List.of(new PerfReportData.LoaderSourceEntry(
                        "test:level", "loader", "LoaderA",
                        new PerfReportData.LoaderPosition(8, 64, 9), 0,
                        List.of(new PerfReportData.LoaderRegion(0, 0, 1, 1)))),
                report.loaderSources());
    }

    private static void sampleEntities(TickSamples samples, Class<?> type, int firstId, int count) {
        for (int index = 0; index < count; index++) {
            samples.sample(snapshot(new TestWork(TickType.ENTITY, type, firstId + index),
                    new BlockPos(1 + index, 64, 1)));
        }
    }

    private static void sampleBlockEntities(TickSamples samples, Class<?> type, int firstX, int count) {
        for (int index = 0; index < count; index++) {
            samples.sample(snapshot(new TestWork(TickType.BLOCK_ENTITY, type, -1),
                    new BlockPos(firstX + index, 64, 2)));
        }
    }

    private static PerfWorkTracker.Snapshot snapshot(TestWork work, BlockPos pos) {
        return new PerfWorkTracker.Snapshot(work.type, ResourceLocation.parse("test:level"), work.workClass, null,
                TickDetailKind.NONE, work.entityId, true,
                pos.getX(), pos.getY(), pos.getZ(), false, null);
    }

    private record TestWork(TickType type, Class<?> workClass, int entityId) {
    }

    private static final class Cow {
    }

    private static final class Cat {
    }

    private static final class Furnace {
    }

    private static final class Pig {
    }

    private static final class Sheep {
    }

    private static final class Zombie {
    }

    private static final class Chest {
    }

    private static final class Hopper {
    }

    private static final class Spawner {
    }

    private static final class ItemEntityMarker {
    }

}
