package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPerfObjectRendererTest {
    @Test
    void clustersOnlyItemEntities() {
        assertTrue(WorldPerfObjectRenderer.shouldClusterEntity(TickDetailKind.ITEM));
        assertFalse(WorldPerfObjectRenderer.shouldClusterEntity(TickDetailKind.ENTITY));
        assertFalse(WorldPerfObjectRenderer.shouldClusterEntity(TickDetailKind.BLOCK));
    }

    @Test
    void drawsGpuParticlesInFiveBatchesWithOrangeReusedForSmoke() {
        assertEquals(PerfReportLimits.MAX_WORLD_HOTSPOT_ENTRIES,
                WorldPerfObjectBuffers.MAX_MARKER_COUNT);
        assertEquals(4, WorldPerfObjectBuffers.TEXELS_PER_MARKER);
        assertEquals(2, WorldPerfObjectBuffers.Source.values().length);
        assertArrayEquals(new WorldPerfObjectBuffers.Batch[]{
                        WorldPerfObjectBuffers.Batch.SMOKE,
                        WorldPerfObjectBuffers.Batch.NORMAL,
                        WorldPerfObjectBuffers.Batch.GLOW,
                        WorldPerfObjectBuffers.Batch.SPARK,
                        WorldPerfObjectBuffers.Batch.LABEL
                }, WorldPerfObjectBuffers.Batch.values());
        assertEquals(40, WorldPerfObjectBuffers.Batch.SMOKE.instancesFor(2));
        assertEquals(128, WorldPerfObjectBuffers.Batch.NORMAL.instancesFor(2));
        assertEquals(32, WorldPerfObjectBuffers.Batch.GLOW.instancesFor(2));
        assertEquals(128, WorldPerfObjectBuffers.Batch.SPARK.instancesFor(2));
        assertEquals(2, WorldPerfObjectBuffers.Batch.LABEL.instancesFor(2));
        assertEquals(24, WorldPerfObjectBuffers.Batch.SMOKE.particleStart());
        assertEquals(64, WorldPerfObjectBuffers.Batch.GLOW.particleStart());
        assertEquals(80, WorldPerfObjectBuffers.Batch.SPARK.particleStart());
    }

    @Test
    void compositesBodyWithPremultipliedAlphaAndEmissionAdditively() {
        assertEquals(WorldPerfObjectBuffers.BlendMode.PREMULTIPLIED,
                WorldPerfObjectBuffers.Batch.SMOKE.blendMode());
        assertEquals(WorldPerfObjectBuffers.BlendMode.PREMULTIPLIED,
                WorldPerfObjectBuffers.Batch.NORMAL.blendMode());
        assertEquals(WorldPerfObjectBuffers.BlendMode.ADDITIVE,
                WorldPerfObjectBuffers.Batch.GLOW.blendMode());
        assertEquals(WorldPerfObjectBuffers.BlendMode.ADDITIVE,
                WorldPerfObjectBuffers.Batch.SPARK.blendMode());
        assertEquals(WorldPerfObjectBuffers.BlendMode.PREMULTIPLIED,
                WorldPerfObjectBuffers.Batch.LABEL.blendMode());
    }

    @Test
    void ordersMarkersByStableEightBlockBuckets() {
        List<WorldEntityFlameClusters.Marker> markers = List.of(
                markerAtDistance(1.0D, 1),
                markerAtDistance(20.0D, 2),
                markerAtDistance(9.0D, 3),
                markerAtDistance(90.0D, 4),
                markerAtDistance(23.0D, 5),
                markerAtDistance(24.0D, 6),
                markerAtDistance(40.0D, 7),
                markerAtDistance(96.0D, 8),
                markerAtDistance(97.0D, 9));

        WorldPerfMarkerBuckets.Batch batch = WorldPerfMarkerBuckets.order(
                markers, new Vec3(0.0D, 64.0D, 0.0D));

        assertEquals(8, batch.size());
        assertEquals(4, batch.sparkBase());
        assertEquals(3, batch.labelBase());
        assertArrayEquals(new int[]{4, 8, 7, 6, 2, 5, 3, 1}, phases(batch));
    }

    @Test
    void keepsDistanceBoundariesStrictForLimitedBatches() {
        assertEquals(0, WorldPerfMarkerBuckets.bucketForDistanceSquared(0.0D));
        assertEquals(0, WorldPerfMarkerBuckets.bucketForDistanceSquared(63.99D));
        assertEquals(1, WorldPerfMarkerBuckets.bucketForDistanceSquared(64.0D));
        assertEquals(3, WorldPerfMarkerBuckets.bucketForDistanceSquared(24.0D * 24.0D));
        assertEquals(5, WorldPerfMarkerBuckets.bucketForDistanceSquared(40.0D * 40.0D));
        assertEquals(11, WorldPerfMarkerBuckets.bucketForDistanceSquared(96.0D * 96.0D));
        assertEquals(-1, WorldPerfMarkerBuckets.bucketForDistanceSquared(96.01D * 96.01D));
        assertEquals(-1, WorldPerfMarkerBuckets.bucketForDistanceSquared(Double.NaN));
    }

    @Test
    void encodesCurrentAndPreviousAnchorsRelativeToStableOrigin() {
        var marker = new WorldEntityFlameClusters.Marker(
                new WorldEntityFlameClusters.Anchor(9.25D, 70.0D, -5.25D),
                new WorldEntityFlameClusters.Anchor(10.25D, 70.5D, -4.75D),
                new PerfReportData.Metric(1.0D, 1.0D, 1L),
                new WorldEntityFlameClusters.Visual(1.0D, 0x7FFF, 1));
        FloatBuffer encoded = FloatBuffer.allocate(WorldPerfObjectBuffers.FLOATS_PER_MARKER);

        WorldPerfObjectBuffers.encodeMarker(marker,
                new WorldPerfObjectBuffers.UploadOrigin(10.0D, -5.0D), encoded);
        encoded.flip();

        assertEquals(16, encoded.remaining());
        assertEquals(0.25F, encoded.get(), 0.0F);
        assertEquals(70.54F, encoded.get(), 0.0001F);
        assertEquals(0.25F, encoded.get(), 0.0F);
        assertEquals(1_024.0F, encoded.get(), 0.0F);
        encoded.position(12);
        assertEquals(-0.75F, encoded.get(), 0.0F);
        assertEquals(70.04F, encoded.get(), 0.0001F);
        assertEquals(-0.25F, encoded.get(), 0.0F);
        assertEquals(0.0F, encoded.get(), 0.0F);
    }

    @Test
    void refreshesStaticAtOneSecondAndDynamicOncePerTick() {
        assertTrue(WorldPerfObjectRenderer.staticRefreshDue(Long.MIN_VALUE, 100L));
        assertFalse(WorldPerfObjectRenderer.staticRefreshDue(100L, 119L));
        assertTrue(WorldPerfObjectRenderer.staticRefreshDue(100L, 120L));
        assertTrue(WorldPerfObjectRenderer.staticRefreshDue(120L, 5L));
        assertFalse(WorldPerfObjectRenderer.dynamicRefreshDue(100L, 100L));
        assertTrue(WorldPerfObjectRenderer.dynamicRefreshDue(100L, 101L));
    }

    private static int[] phases(WorldPerfMarkerBuckets.Batch batch) {
        int[] phases = new int[batch.size()];
        for (int index = 0; index < phases.length; index++) {
            phases[index] = batch.markers()[index].phase();
        }
        return phases;
    }

    private static WorldEntityFlameClusters.Marker markerAtDistance(
            double distance, int phase) {
        return new WorldEntityFlameClusters.Marker(
                new WorldEntityFlameClusters.Anchor(distance, 64.0D, 0.0D),
                new PerfReportData.Metric(1.0D, 1.0D, 1L),
                new WorldEntityFlameClusters.Visual(1.0D, phase, 1));
    }
}
