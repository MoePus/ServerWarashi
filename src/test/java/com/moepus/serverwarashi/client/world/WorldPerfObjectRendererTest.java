package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

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
        assertEquals(4_096, WorldPerfObjectBuffers.MAX_MARKER_COUNT);
        assertEquals(5, WorldPerfObjectBuffers.Batch.values().length);
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
        assertEquals(0, WorldPerfObjectBuffers.Batch.NORMAL.renderPass());
        assertEquals(1, WorldPerfObjectBuffers.Batch.GLOW.renderPass());
        assertEquals(2, WorldPerfObjectBuffers.Batch.SMOKE.renderPass());
        assertEquals(3, WorldPerfObjectBuffers.Batch.LABEL.renderPass());
        assertEquals(4, WorldPerfObjectBuffers.Batch.SPARK.renderPass());
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
    void convertsWorldAnchorsToCameraRelativeDoublesBeforeUpload() {
        var marker = new WorldEntityFlameClusters.Marker(
                new WorldEntityFlameClusters.Anchor(30_000_000.25D, 70.5D, -29_999_999.75D),
                new PerfReportData.Metric(1.0D, 1.0D, 1L),
                new WorldEntityFlameClusters.Visual(1.0D, 0, 1));
        WorldPerfObjectBuffers.Entry entry = WorldPerfObjectBuffers.Entry.from(
                marker, new Vec3(30_000_000.0D, 64.0D, -30_000_000.0D), 42.0D);

        assertEquals(0.25D, entry.x());
        assertEquals(6.5D, entry.y());
        assertEquals(0.25D, entry.z());
        assertEquals(42.0D, entry.distanceSquared());
    }

    @Test
    void encodesOneCompactMarkerRecordWithoutParticleVertices() {
        var marker = new WorldEntityFlameClusters.Marker(
                new WorldEntityFlameClusters.Anchor(10.25D, 70.5D, -4.75D),
                new PerfReportData.Metric(1.0D, 1.0D, 1L),
                new WorldEntityFlameClusters.Visual(1.0D, 0x7FFF, 1));
        WorldPerfObjectBuffers.Entry entry = WorldPerfObjectBuffers.Entry.from(
                marker, new Vec3(10.0D, 64.0D, -5.0D), 1.0D);
        FloatBuffer encoded = FloatBuffer.allocate(WorldPerfObjectBuffers.FLOATS_PER_MARKER);

        WorldPerfObjectBuffers.encodeMarker(entry, encoded);
        encoded.flip();

        assertEquals(12, encoded.remaining());
        assertEquals(0.25F, encoded.get(), 0.0F);
        assertEquals(6.54F, encoded.get(), 0.0001F);
        assertEquals(0.25F, encoded.get(), 0.0F);
        assertEquals(1_024.0F, encoded.get(), 0.0F);
        assertEquals(0x40 / 255.0F, encoded.get(), 0.0001F);
        assertEquals(0xDC / 255.0F, encoded.get(), 0.0001F);
        assertEquals(0x78 / 255.0F, encoded.get(), 0.0001F);
        assertEquals(511.0F, encoded.get(), 0.0F);
        assertEquals(1_000.0F, encoded.get(), 0.0F);
        assertEquals(-1.0F, encoded.get(), 0.0F);
        assertEquals(WorldOverlayMath.packLabelStrength(1.0D) / 255.0F,
                encoded.get(), 0.0001F);
        assertEquals(0.0F, encoded.get(), 0.0F);
    }
}
