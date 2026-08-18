package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfReportCodecTest {
    @Test
    void roundTripsTypedReport() {
        PerfReportData.Metric metric = new PerfReportData.Metric(1.25D, 3.5D, 42L);
        PerfReportData expected = new PerfReportData(
                17L, 48.75D, 10_000L, 123_456_789L, 200L, 9_999L, 1_000, 2, 3, 4, 5,
                List.of(new PerfReportData.ChunkEntry("minecraft:overworld", 2, -3, 7, 4,
                        List.of(new PerfReportData.ObjectCount("ItemEntity", "minecraft:stone",
                                TickDetailKind.ITEM, 3, 33, 64, -40)),
                        List.of(new PerfReportData.ObjectCount("FurnaceBlockEntity", "",
                                TickDetailKind.NONE, 2, 35, 63, -38)), metric)),
                List.of(new PerfReportData.TickableEntry(TickType.ENTITY, "minecraft:overworld",
                                "ItemEntity", "minecraft:stone", TickDetailKind.ITEM,
                                17, true, 33, 64, -40, false, null, metric),
                        new PerfReportData.TickableEntry(TickType.HANDLER, "",
                                "ServerEvents#onTick(ServerTickEvent.Pre)", "", TickDetailKind.NONE,
                                -1, false, 0, 0, 0, false,
                                "com.example.ServerEvents#onTick(ServerTickEvent.Pre)", metric),
                        new PerfReportData.TickableEntry(TickType.GC, "", "Rolling 1s", "",
                                TickDetailKind.NONE, -1, false, 0, 0, 0, false, "",
                                new PerfReportData.Metric(2.5D, 12.0D, 20L))),
                List.of(new PerfReportData.OwnerEntry(PerfReportData.AttributionKind.PLAYER,
                        "uuid", "Player", false, 123_000L,
                        new PerfReportData.Position("minecraft:overworld", 32, 64, -48), 25, metric,
                        List.of(new PerfReportData.OwnerSourceEntry("self", "Self", null, 20, metric),
                                new PerfReportData.OwnerSourceEntry("loader", "LoaderA",
                                        new PerfReportData.Position("minecraft:overworld", 40, 63, -40),
                                        5, metric)))),
                List.of(new PerfReportData.LoaderSourceEntry(
                        "minecraft:overworld", "loader-id", "minecraft:lodestone",
                        new PerfReportData.LoaderPosition(40, 63, -40), 2,
                        List.of(new PerfReportData.LoaderRegion(2, -3, 7, 7)),
                        new PerfReportData.LoaderControl(
                                ResourceLocation.parse("test:loader"), new BlockPos(40, 63, -40), true, false)))
        );
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfReportCodec.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PerfReportCodec.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsLivePayloadState() {
        PerfReportPayload expected = new PerfReportPayload(
                PerfReportData.empty(1_000), PerfReportPayload.Mode.UPDATE, true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfReportPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PerfReportPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsMapSubscription() {
        PerfMapSubscriptionPayload expected = new PerfMapSubscriptionPayload(true);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfMapSubscriptionPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PerfMapSubscriptionPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsPerfControl() {
        PerfControlPayload expected = new PerfControlPayload(PerfControlPayload.Action.CLEAR);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfControlPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, PerfControlPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsLoaderFreezeActionAndAuthoritativeState() {
        ResourceLocation dimension = ResourceLocation.parse("minecraft:overworld");
        ResourceLocation controller = ResourceLocation.parse("test:loader");
        BlockPos owner = new BlockPos(12, 64, -7);
        LoaderFreezeActionPayload action = new LoaderFreezeActionPayload(
                9L, dimension, controller, owner, true);
        LoaderControlStatePayload state = new LoaderControlStatePayload(
                9L, dimension, controller, owner, true, false,
                LoaderControlStatePayload.Status.APPLIED);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            LoaderFreezeActionPayload.STREAM_CODEC.encode(buffer, action);
            assertEquals(action, LoaderFreezeActionPayload.STREAM_CODEC.decode(buffer));
            LoaderControlStatePayload.STREAM_CODEC.encode(buffer, state);
            LoaderControlStatePayload decoded = LoaderControlStatePayload.STREAM_CODEC.decode(buffer);
            assertEquals(state, decoded);
            assertTrue(decoded.hasAuthoritativeState());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsMoreThanTotalLoaderRegionBudgetWhenWriting() {
        var region = new PerfReportData.LoaderRegion(0, 0, 1, 1);
        var first = new PerfReportData.LoaderSourceEntry("test:level", "loader-1", "Loader", null, 0,
                Collections.nCopies(2_049, region));
        var second = new PerfReportData.LoaderSourceEntry("test:level", "loader-2", "Loader", null, 0,
                Collections.nCopies(2_049, region));
        var report = new PerfReportData(0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(first, second));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> PerfReportCodec.STREAM_CODEC.encode(buffer, report));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsMoreThanTotalLoaderRegionBudgetWhenReading() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            writeEmptyReportHeader(buffer);
            buffer.writeVarInt(2);
            writeLoaderSourceHeader(buffer, 2_049);
            for (int index = 0; index < 2_049; index++) {
                writeUnitRegion(buffer);
            }
            writeLoaderSourceHeader(buffer, 2_049);

            assertThrows(IllegalArgumentException.class,
                    () -> PerfReportCodec.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedAndCumulativeStringsWhenWriting() {
        String tooLong = "x".repeat(PerfReportLimits.MAX_STRING_LENGTH + 1);
        assertCodecWriteFails(reportWithLoaders(List.of(loader(tooLong, "Loader"))));

        String repeated = "x".repeat(200);
        var source = loader(repeated, repeated);
        assertCodecWriteFails(reportWithLoaders(
                Collections.nCopies(PerfReportLimits.MAX_LOADER_SOURCES, source)));
    }

    @Test
    void rejectsCumulativeStringsWhenReading() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            writeEmptyReportHeader(buffer);
            buffer.writeVarInt(PerfReportLimits.MAX_LOADER_SOURCES);
            String repeated = "x".repeat(200);
            for (int index = 0; index < PerfReportLimits.MAX_LOADER_SOURCES; index++) {
                buffer.writeUtf(repeated);
                buffer.writeUtf(repeated);
                buffer.writeUtf(repeated);
                buffer.writeBoolean(false);
                buffer.writeBoolean(false);
                buffer.writeVarInt(0);
                buffer.writeVarInt(0);
            }

            assertThrows(IllegalArgumentException.class,
                    () -> PerfReportCodec.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidLoaderRegionDimensionsAndOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerfReportData.LoaderRegion(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PerfReportData.LoaderRegion(0, 0, 4_097, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PerfReportData.LoaderRegion(Integer.MAX_VALUE, 0, 2, 1));
        assertEquals(Integer.MAX_VALUE,
                new PerfReportData.LoaderRegion(Integer.MAX_VALUE, 0, 1, 1).minChunkX());
    }

    @Test
    void rejectsNegativeOmissionCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerfReportData(0.0D, 0L, 0L, 0L, 0L, 1_000,
                        -1, 0, 0, 0, List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PerfReportData.LoaderSourceEntry(
                        "test:level", "loader", "Loader", null, -1, List.of()));
    }

    @Test
    void keepsOneHundredSquareLoadersCompactOnWire() {
        List<PerfReportData.LoaderSourceEntry> sources = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            sources.add(new PerfReportData.LoaderSourceEntry("test:level", "loader-" + index,
                    "test:loader", new PerfReportData.LoaderPosition(index * 160, 64, 0), 0,
                    List.of(new PerfReportData.LoaderRegion(index * 10, 0, 7, 7))));
        }
        var report = new PerfReportData(0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), sources);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PerfReportCodec.STREAM_CODEC.encode(buffer, report);
            assertTrue(buffer.readableBytes() < 8_000, "Encoded report was " + buffer.readableBytes() + " bytes");
        } finally {
            buffer.release();
        }
    }

    private static PerfReportData reportWithLoaders(List<PerfReportData.LoaderSourceEntry> sources) {
        return new PerfReportData(0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), sources);
    }

    private static PerfReportData.LoaderSourceEntry loader(String sourceId, String name) {
        return new PerfReportData.LoaderSourceEntry("test:level", sourceId, name, null, 0, List.of());
    }

    private static void assertCodecWriteFails(PerfReportData report) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(RuntimeException.class,
                    () -> PerfReportCodec.STREAM_CODEC.encode(buffer, report));
        } finally {
            buffer.release();
        }
    }

    private static void writeEmptyReportHeader(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(0L);
        buffer.writeDouble(0.0D);
        buffer.writeVarLong(0L);
        buffer.writeVarLong(0L);
        buffer.writeVarLong(0L);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(1_000);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
    }

    private static void writeLoaderSourceHeader(RegistryFriendlyByteBuf buffer, int regions) {
        buffer.writeUtf("test:level");
        buffer.writeUtf("loader");
        buffer.writeUtf("Loader");
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeVarInt(regions);
    }

    private static void writeUnitRegion(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(1);
    }
}
