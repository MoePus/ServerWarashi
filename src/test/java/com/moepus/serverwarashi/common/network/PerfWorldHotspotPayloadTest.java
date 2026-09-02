package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfWorldHotspotPayloadTest {
    @Test
    void roundTripsCompactEntriesWithoutUuidIdentity() {
        var entity = new PerfWorldHotspotPayload.Entry(
                PerfWorldHotspotIndex.Key.entity(42), TickDetailKind.ENTITY,
                7, 17L, 123L, 640);
        var blockEntity = new PerfWorldHotspotPayload.Entry(
                PerfWorldHotspotIndex.Key.blockEntity(3, 64, -2),
                TickDetailKind.BLOCK, 11, 1L, 9L, 384);
        var expected = new PerfWorldHotspotPayload(5L, 0L, 20L, 20L, 1_250_000L,
                ResourceLocation.parse("minecraft:overworld"), true,
                List.of(entity, blockEntity), List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfWorldHotspotPayload.STREAM_CODEC.encode(buffer, expected);
            assertTrue(buffer.readableBytes() < 64, "Encoded payload was " + buffer.readableBytes());
            assertEquals(expected, PerfWorldHotspotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsDeltaFromRevisionZero() {
        var expected = new PerfWorldHotspotPayload(8L, 0L, 1L, 1L, 1_000_000L,
                ResourceLocation.parse("minecraft:overworld"), false, List.of(), List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfWorldHotspotPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PerfWorldHotspotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsNonCanonicalTaggedKeys() {
        assertThrows(IllegalArgumentException.class, () -> new PerfWorldHotspotIndex.Key(
                PerfWorldHotspotIndex.Kind.ENTITY, 1, 2L));
        assertThrows(IllegalArgumentException.class, () -> new PerfWorldHotspotIndex.Key(
                PerfWorldHotspotIndex.Kind.BLOCK_ENTITY, 1, 2L));
    }

    @Test
    void rejectsEntriesWhoseIdentityAndDetailsDisagree() {
        ResourceLocation stone = ResourceLocation.parse("minecraft:stone");
        var blockKey = PerfWorldHotspotIndex.Key.blockEntity(3, 64, -2);

        assertThrows(IllegalArgumentException.class, () ->
                new PerfWorldHotspotIndex.Entry(blockKey, "minecraft:overworld",
                        4, 64, -2, TickDetailKind.BLOCK, stone, 1L, 1L, 256));
        assertThrows(IllegalArgumentException.class, () ->
                new PerfWorldHotspotIndex.Entry(blockKey, "minecraft:overworld",
                        3, 64, -2, TickDetailKind.ENTITY, stone, 1L, 1L, 256));
        assertThrows(IllegalArgumentException.class, () ->
                new PerfWorldHotspotPayload.Entry(
                        blockKey, TickDetailKind.ENTITY, 1, 1L, 1L, 256));
        assertThrows(IllegalArgumentException.class, () ->
                new PerfWorldHotspotPayload.Entry(
                        PerfWorldHotspotIndex.Key.entity(4),
                        TickDetailKind.BLOCK, 1, 1L, 1L, 256));
    }

    @Test
    void permitsStressTestHotspotLimitButRejectsLargerPayloads() {
        var entry = new PerfWorldHotspotPayload.Entry(
                PerfWorldHotspotIndex.Key.entity(42), TickDetailKind.ENTITY,
                7, 1L, 1L, 256);
        List<PerfWorldHotspotPayload.Entry> maximum = java.util.Collections.nCopies(
                PerfReportLimits.MAX_WORLD_HOTSPOT_ENTRIES, entry);

        new PerfWorldHotspotPayload(1L, 0L, 1L, 1L, 1_000_000L,
                ResourceLocation.parse("minecraft:overworld"), true,
                maximum, List.of());
        assertThrows(IllegalArgumentException.class, () -> new PerfWorldHotspotPayload(
                1L, 0L, 1L, 1L, 1_000_000L,
                ResourceLocation.parse("minecraft:overworld"), true,
                java.util.Collections.nCopies(
                        PerfReportLimits.MAX_WORLD_HOTSPOT_ENTRIES + 1, entry),
                List.of()));
    }
}
