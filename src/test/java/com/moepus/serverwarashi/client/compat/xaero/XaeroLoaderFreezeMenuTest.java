package com.moepus.serverwarashi.client.compat.xaero;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XaeroLoaderFreezeMenuTest {
    private static final long REPORT_ID = 9L;
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final PerfReportData.LoaderRegion HOVERED_REGION =
            new PerfReportData.LoaderRegion(-2, -3, 2, 2);

    @Test
    void selectsSupportedHoveredLoadersWithStableIdentityOrdering() {
        BlockPos firstPos = new BlockPos(-8, 70, -24);
        BlockPos secondPos = new BlockPos(-1, 64, -17);
        var second = loader(new Fixture("second", "test:z", secondPos,
                false, 0, "minecraft:overworld", HOVERED_REGION));
        var first = loader(new Fixture("first", "test:a", firstPos,
                true, 0, "minecraft:overworld", HOVERED_REGION));
        var duplicate = loader(new Fixture("duplicate", "test:a", firstPos,
                true, 0, "minecraft:overworld", HOVERED_REGION));
        var unsupported = unsupported("unsupported", HOVERED_REGION);
        var otherDimension = loader(new Fixture("nether", "test:nether", secondPos,
                false, 0, "minecraft:the_nether", HOVERED_REGION));
        var truncated = loader(new Fixture("truncated", "test:truncated", secondPos,
                false, 1, "minecraft:overworld", HOVERED_REGION));
        var report = report(REPORT_ID,
                List.of(second, first, duplicate, unsupported, otherDimension, truncated));

        var targets = XaeroLoaderFreezeMenu.targets(report,
                new XaeroLoaderFreezeMenu.Location(REPORT_ID, OVERWORLD, -1, -2));

        assertEquals(List.of("test:a", "test:z"), targets.stream()
                .map(target -> target.controllerId().toString()).toList());
        assertFalse(targets.getFirst().requestedFrozen());
        assertTrue(targets.getLast().requestedFrozen());
    }

    @Test
    void selectsOnlyTheLoaderPlacementChunkAndRejectsStaleReports() {
        var loadedRegion = new PerfReportData.LoaderRegion(0, 0, 2, 1);
        var source = loader(new Fixture("source", "test:loader", BlockPos.ZERO,
                false, 0, "minecraft:overworld", loadedRegion));
        var report = report(REPORT_ID, List.of(source));

        assertTrue(XaeroLoaderFreezeMenu.targets(report,
                new XaeroLoaderFreezeMenu.Location(REPORT_ID, OVERWORLD, 1, 0)).isEmpty());
        assertEquals(1, XaeroLoaderFreezeMenu.targets(report,
                new XaeroLoaderFreezeMenu.Location(REPORT_ID, OVERWORLD, 0, 0)).size());
        assertTrue(XaeroLoaderFreezeMenu.targets(report,
                new XaeroLoaderFreezeMenu.Location(REPORT_ID - 1L, OVERWORLD, 0, 0)).isEmpty());
        assertTrue(XaeroLoaderFreezeMenu.targets(report(0L, List.of(source)),
                new XaeroLoaderFreezeMenu.Location(0L, OVERWORLD, 0, 0)).isEmpty());
    }

    private static PerfReportData.LoaderSourceEntry loader(Fixture fixture) {
        ResourceLocation controller = ResourceLocation.parse(fixture.controller());
        var control = new PerfReportData.LoaderControl(
                controller, fixture.owner(), fixture.frozen(), !fixture.frozen());
        return new PerfReportData.LoaderSourceEntry(
                fixture.dimension(), fixture.id(), fixture.id(),
                new PerfReportData.LoaderPosition(
                        fixture.owner().getX(), fixture.owner().getY(), fixture.owner().getZ()),
                fixture.omitted(), List.of(fixture.region()), control);
    }

    private static PerfReportData.LoaderSourceEntry unsupported(
            String id, PerfReportData.LoaderRegion region) {
        return new PerfReportData.LoaderSourceEntry(
                OVERWORLD.toString(), id, id, null, 0, List.of(region));
    }

    private static PerfReportData report(
            long reportId, List<PerfReportData.LoaderSourceEntry> loaders) {
        return new PerfReportData(reportId, 0.0D, 0L, 0L, 0L, 0L, 1_000,
                0, 0, 0, 0, List.of(), List.of(), List.of(), loaders);
    }

    private record Fixture(String id, String controller, BlockPos owner, boolean frozen,
                           int omitted, String dimension, PerfReportData.LoaderRegion region) {
    }
}
