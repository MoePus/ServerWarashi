package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.network.LoaderFreezeActionPayload;
import com.moepus.serverwarashi.common.network.WeakChunkClearPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.moepus.serverwarashi.testsupport.PerfReportFixtures.reportBuilder;

class MapLoaderFreezeMenuTest {
    private static final long REPORT_ID = 9L;
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final MapTarget HOVERED_CHUNK = new MapTarget(OVERWORLD, -1, -2);
    private static final PerfReportData.LoaderRegion HOVERED_REGION =
            new PerfReportData.LoaderRegion(-2, -3, 2, 2);

    @Test
    void selectsSupportedHoveredLoadersWithStableIdentityOrdering() {
        BlockPos firstPos = new BlockPos(-8, 70, -24);
        BlockPos secondPos = new BlockPos(-1, 64, -17);
        var second = loader(new Fixture("second", "test:z", secondPos,
                false, 0, OVERWORLD.toString(), HOVERED_REGION));
        var first = loader(new Fixture("first", "test:a", firstPos,
                true, 0, OVERWORLD.toString(), HOVERED_REGION));
        var duplicate = loader(new Fixture("duplicate", "test:a", firstPos,
                true, 0, OVERWORLD.toString(), HOVERED_REGION));
        var unsupported = unsupported("unsupported", HOVERED_REGION);
        var otherDimension = loader(new Fixture("nether", "test:nether", secondPos,
                false, 0, "minecraft:the_nether", HOVERED_REGION));
        var truncated = loader(new Fixture("truncated", "test:truncated", secondPos,
                false, 1, OVERWORLD.toString(), HOVERED_REGION));
        var report = report(REPORT_ID,
                List.of(second, first, duplicate, unsupported, otherDimension, truncated));

        var targets = MapContextMenuService.loaderTargets(report, REPORT_ID, HOVERED_CHUNK);

        assertEquals(List.of("test:a", "test:z"), targets.stream()
                .map(target -> target.controllerId().toString()).toList());
        assertFalse(targets.getFirst().requestedFrozen());
        assertTrue(targets.getLast().requestedFrozen());
    }

    @Test
    void selectsOnlyPlacementChunkAndRejectsStaleReports() {
        var loadedRegion = new PerfReportData.LoaderRegion(0, 0, 2, 1);
        var source = loader(new Fixture("source", "test:loader", BlockPos.ZERO,
                false, 0, OVERWORLD.toString(), loadedRegion));
        var report = report(REPORT_ID, List.of(source));

        assertTrue(MapContextMenuService.loaderTargets(report, REPORT_ID,
                new MapTarget(OVERWORLD, 1, 0)).isEmpty());
        assertEquals(1, MapContextMenuService.loaderTargets(report, REPORT_ID,
                new MapTarget(OVERWORLD, 0, 0)).size());
        assertTrue(MapContextMenuService.loaderTargets(
                report, REPORT_ID - 1L, new MapTarget(OVERWORLD, 0, 0)).isEmpty());
        assertTrue(MapContextMenuService.loaderTargets(report(0L, List.of(source)), 0L,
                new MapTarget(OVERWORLD, 0, 0)).isEmpty());
    }

    @Test
    void limitsVisibleControlsAndActionsCarryRequestedState() {
        List<PerfReportData.LoaderSourceEntry> sources = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            String suffix = String.format("%02d", index);
            sources.add(loader(new Fixture("loader " + suffix, "test:loader_" + suffix,
                    new BlockPos(-1, 64, -17), index == 0, 0,
                    OVERWORLD.toString(), HOVERED_REGION)));
        }
        PerfReportData report = report(REPORT_ID, sources);
        var clients = new RecordingClients();
        var state = new MapContextMenuService.MenuState(null, report, REPORT_ID);

        List<MapMenuItem> items = MapContextMenuService.items(HOVERED_CHUNK, state, clients);

        assertEquals(13, items.size());
        assertEquals("gui.serverwarashi.loader_unfreeze", key(items.getFirst()));
        MapMenuItem more = items.getLast();
        assertEquals("gui.serverwarashi.loader_control_more", key(more));
        assertFalse(more.active());

        items.getFirst().action().run();
        more.action().run();
        assertEquals(1, clients.loaderRequests.size());
        LoaderFreezeActionPayload request = clients.loaderRequests.getFirst();
        assertEquals(REPORT_ID, request.reportId());
        assertEquals(OVERWORLD, request.dimension());
        assertEquals(ResourceLocation.parse("test:loader_00"), request.controllerId());
        assertFalse(request.frozen());
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
        return reportBuilder().reportId(reportId).loaderSources(loaders).build();
    }

    private static String key(MapMenuItem item) {
        return ((TranslatableContents) item.label().getContents()).getKey();
    }

    private record Fixture(String id,
                           String controller,
                           BlockPos owner,
                           boolean frozen,
                           int omitted,
                           String dimension,
                           PerfReportData.LoaderRegion region) {
    }

    private static final class RecordingClients implements MapContextMenuService.ClientActions {
        private final List<LoaderFreezeActionPayload> loaderRequests = new ArrayList<>();

        @Override
        public boolean canClearWeakChunk() {
            return false;
        }

        @Override
        public void clearWeakChunk(WeakChunkClearPayload payload) {
        }

        @Override
        public boolean canControlLoader() {
            return true;
        }

        @Override
        public void controlLoader(LoaderFreezeActionPayload payload) {
            loaderRequests.add(payload);
        }
    }
}
