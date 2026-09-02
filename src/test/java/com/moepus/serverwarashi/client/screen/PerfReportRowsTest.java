package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfReportRowsTest {
    @Test
    void chunksSortByMsptDescending() {
        PerfReportData report = report(
                List.of(chunk("slow", 1, 2, metric(4.0, 3.0)),
                        chunk("fast", 3, 4, metric(1.0, 8.0))),
                List.of(),
                List.of());

        List<PerfReportRow> rows = PerfReportRows.chunks(report, PerfReportRows.SortKey.MSPT, Map.of());

        assertEquals(List.of("slow [1, 2]", "fast [3, 4]"), labels(rows));
        assertEquals(3.0, rows.get(0).metric().max());
    }

    @Test
    void tickablesSortByMaxDescending() {
        PerfReportData report = report(
                List.of(),
                List.of(tickable("FastTick", metric(5.0, 2.0)),
                        tickable("SpikyTick", metric(1.0, 9.0))),
                List.of());

        List<PerfReportRow> rows = PerfReportRows.tickables(report, PerfReportRows.SortKey.MAX, Map.of());

        assertEquals(List.of("SpikyTick#1 @ test (1, 2, 3)",
                "FastTick#1 @ test (1, 2, 3)"), labels(rows));
        assertEquals(List.of("screen.serverwarashi.profiler.type.entity",
                        "screen.serverwarashi.profiler.type.entity"),
                rows.stream().map(row -> row.auxiliary().type()).toList());
        assertEquals(1.0, rows.get(0).metric().mspt());
    }

    @Test
    void itemEntityDisplaysTranslatedRegistryId() {
        PerfReportData report = report(List.of(), List.of(new PerfReportData.TickableEntry(
                TickType.ENTITY, "test", "net.minecraft.world.entity.item.ItemEntity", "minecraft:paper",
                TickDetailKind.ITEM, 7, true, 1, 2, 3, false, "", metric(1.0, 1.0))), List.of());

        assertEquals("Paper#7 @ test (1, 2, 3)", labels(
                PerfReportRows.tickables(report, PerfReportRows.SortKey.MSPT, Map.of())) .getFirst());
    }

    @Test
    void handlerUsesShortLabelAndRetainsFullNameForTooltip() {
        String fullName = "com.example.ServerEvents#onTick("
                + "net.neoforged.neoforge.event.tick.ServerTickEvent.Pre)";
        var handler = new PerfReportData.TickableEntry(
                TickType.HANDLER, "", "ServerEvents#onTick(ServerTickEvent.Pre)", "",
                TickDetailKind.NONE, -1, false, 0, 0, 0, false, fullName, metric(2.0, 3.0));

        PerfReportRow row = PerfReportRows.tickables(
                report(List.of(), List.of(handler), List.of()), PerfReportRows.SortKey.MSPT, Map.of()).getFirst();

        assertEquals("ServerEvents#onTick(ServerTickEvent.Pre)", row.label());
        assertEquals("screen.serverwarashi.profiler.type.handler", row.auxiliary().type());
        assertEquals(fullName + "\nscreen.serverwarashi.profiler.attribution_quality", tooltip(row));
        assertEquals("", row.content().coordinate());
        assertTrue(row.capabilities().coordinateAction().isEmpty());
        assertTrue(row.capabilities().expansion().isEmpty());
    }

    @Test
    void unknownFallbackUsesOneLocalizedTaskRow() {
        var unknown = new PerfReportData.TickableEntry(
                TickType.TASK, "", "Unknown", "", TickDetailKind.NONE,
                -1, false, 0, 0, 0, false, "", metric(2.0, 3.0));

        PerfReportRow row = PerfReportRows.tickables(
                report(List.of(), List.of(unknown), List.of()),
                PerfReportRows.SortKey.MSPT, Map.of()).getFirst();

        assertEquals("screen.serverwarashi.profiler.tickable.unknown", row.label());
        assertEquals("screen.serverwarashi.profiler.type.task", row.auxiliary().type());
        assertEquals("screen.serverwarashi.profiler.attribution_quality", tooltip(row));
        assertTrue(row.capabilities().labelAction().isEmpty());
        assertTrue(row.capabilities().coordinateAction().isEmpty());
        assertTrue(row.capabilities().expansion().isEmpty());
    }

    @Test
    void negotiatedUnknownFallbackHasOnlyLabelTooltipAndAction() {
        var unknown = new PerfReportData.TickableEntry(
                TickType.TASK, "", "Unknown", "", TickDetailKind.NONE,
                -1, false, 0, 0, 0, false, "", metric(2.0, 3.0));

        PerfReportRow row = PerfReportRows.tickables(
                report(List.of(), List.of(unknown), List.of()),
                PerfReportRows.SortKey.MSPT, Map.of(), true).getFirst();

        assertEquals("screen.serverwarashi.profiler.unknown.click\n"
                + "screen.serverwarashi.profiler.attribution_quality", tooltip(row));
        assertTrue(row.capabilities().labelAction().isPresent());
        assertTrue(row.capabilities().coordinateAction().isEmpty());
        assertTrue(row.capabilities().expansion().isEmpty());
    }

    @Test
    void gcAppearsAsARollingTickableMetric() {
        var gc = new PerfReportData.TickableEntry(
                TickType.GC, "", "Rolling 1s", "", TickDetailKind.NONE,
                -1, false, 0, 0, 0, false, "", metric(5.0D, 100.0D, 20L));

        PerfReportRow row = PerfReportRows.tickables(
                report(List.of(), List.of(tickable("HotTick", metric(50.0D, 200.0D)), gc), List.of()),
                PerfReportRows.SortKey.MSPT, Map.of()).getFirst();

        assertEquals("screen.serverwarashi.profiler.tickable.gc", row.label());
        assertEquals("screen.serverwarashi.profiler.type.gc", row.auxiliary().type());
        assertEquals(new PerfReportData.Metric(5.0D, 100.0D, 20L), row.metric());
        assertEquals("screen.serverwarashi.profiler.attribution_quality", tooltip(row));
    }

    @Test
    void objectTranslationOnlyUsesPrefixesAllowedByDetailKind() {
        assertEquals("Paper", PerfDisplayText.objectName(
                TickDetailKind.ITEM, "ItemEntity", "minecraft:paper"));
        assertEquals("Cat", PerfDisplayText.objectName(
                TickDetailKind.ENTITY, "Cat", "minecraft:cat"));
        assertEquals("Furnace", PerfDisplayText.objectName(
                TickDetailKind.BLOCK, "FurnaceBlockEntity", "minecraft:furnace"));
        assertEquals("minecraft:cow", PerfDisplayText.objectName(
                TickDetailKind.BLOCK, "TestBlockEntity", "minecraft:cow"));
    }

    @Test
    void expandedChunkShowsEntityAndBlockEntityTopThree() {
        String key = "chunk:test:0:0";
        PerfReportData.ChunkEntry chunk = new PerfReportData.ChunkEntry("test", 0, 0, 6, 5,
                List.of(new PerfReportData.ObjectCount("Cow", "minecraft:cow",
                                TickDetailKind.ENTITY, 3, 1, 64, 2),
                        new PerfReportData.ObjectCount("ItemEntity", "minecraft:paper",
                                TickDetailKind.ITEM, 2, 3, 64, 4),
                        new PerfReportData.ObjectCount("Sheep", "minecraft:sheep",
                                TickDetailKind.ENTITY, 1, 5, 64, 6)),
                List.of(new PerfReportData.ObjectCount("ChestBlockEntity", "minecraft:chest",
                                TickDetailKind.BLOCK, 3, 7, 63, 8),
                        new PerfReportData.ObjectCount("FurnaceBlockEntity", "minecraft:furnace",
                                TickDetailKind.BLOCK, 1, 9, 62, 10)),
                metric(3.0, 2.0));

        List<PerfReportRow> rows = PerfReportRows.chunks(report(List.of(chunk), List.of(), List.of()),
                PerfReportRows.SortKey.MSPT, Set.of(key), Map.of());

        assertEquals(List.of("test [0, 0]", "Cow(1, 64, 2)", "Paper(3, 64, 4)",
                        "Sheep(5, 64, 6)", "Chest(7, 63, 8)",
                        "Furnace(9, 62, 10)"),
                labels(rows));
        assertEquals(List.of(6, 3, 2, 1, 0, 0), rows.stream()
                .map(row -> row.auxiliary().entityCount()).toList());
        assertEquals(List.of(5, 0, 0, 0, 3, 1), rows.stream()
                .map(row -> row.auxiliary().blockEntityCount()).toList());
        assertEquals("execute in test run tp @s 1 64 2", coordinateCommand(rows.get(1)));
        assertFalse(rows.get(1).showsMetric());
        assertTrue(rows.getFirst().capabilities().expansion().orElseThrow().expanded());
        assertEquals("execute in test run tp @s 8 ~ 8", coordinateCommand(rows.getFirst()));
    }

    @Test
    void chunksSortByEntityBlockEntityAndSampleHeaders() {
        PerfReportData report = report(List.of(
                chunk("many-entities", 0, 0, 9, 1, metric(1.0, 1.0, 10)),
                chunk("many-block-entities", 0, 0, 1, 8, metric(1.0, 1.0, 20)),
                chunk("many-samples", 0, 0, 2, 2, metric(1.0, 1.0, 30))),
                List.of(), List.of());

        assertEquals("many-entities [0, 0]", PerfReportRows.chunks(
                report, PerfReportRows.SortKey.ENTITIES, Map.of()).getFirst().label());
        assertEquals("many-block-entities [0, 0]", PerfReportRows.chunks(
                report, PerfReportRows.SortKey.BLOCK_ENTITIES, Map.of()).getFirst().label());
        assertEquals("many-samples [0, 0]", PerfReportRows.chunks(
                report, PerfReportRows.SortKey.SAMPLES, Map.of()).getFirst().label());
    }

    @Test
    void sortableHeadersMatchEachTableLayout() {
        PerfReportGeometry geometry = new PerfReportGeometry(0, 0, 760, 400, 12, 730, 100, 380);

        assertEquals(List.of(PerfReportRows.SortKey.ENTITIES, PerfReportRows.SortKey.BLOCK_ENTITIES,
                        PerfReportRows.SortKey.SAMPLES, PerfReportRows.SortKey.MSPT, PerfReportRows.SortKey.MAX),
                headerSorts(geometry, PerfReportTableRenderer.Layout.CHUNKS));
        assertEquals(List.of(PerfReportRows.SortKey.SAMPLES, PerfReportRows.SortKey.MSPT,
                        PerfReportRows.SortKey.MAX),
                headerSorts(geometry, PerfReportTableRenderer.Layout.TICKABLES));
        assertEquals(List.of(PerfReportRows.SortKey.CHUNKS, PerfReportRows.SortKey.SAMPLES,
                        PerfReportRows.SortKey.MSPT, PerfReportRows.SortKey.MAX),
                headerSorts(geometry, PerfReportTableRenderer.Layout.PLAYERS));
    }

    @Test
    void selectedHeaderIsBoldAndOnlyTurnsItalicWhileOrderIsStale() {
        var fresh = new PerfReportTableRenderer.HeaderState(PerfReportRows.SortKey.MSPT, false, 0, 0);
        var stale = new PerfReportTableRenderer.HeaderState(PerfReportRows.SortKey.MSPT, true, 0, 0);

        assertTrue(PerfReportTableRenderer.headerLabel("MSPT", PerfReportRows.SortKey.MSPT, fresh)
                .getStyle().isBold());
        assertFalse(PerfReportTableRenderer.headerLabel("MSPT", PerfReportRows.SortKey.MSPT, fresh)
                .getStyle().isItalic());
        assertTrue(PerfReportTableRenderer.headerLabel("MSPT", PerfReportRows.SortKey.MSPT, stale)
                .getStyle().isItalic());
        assertFalse(PerfReportTableRenderer.headerLabel("MAX", PerfReportRows.SortKey.MAX, stale)
                .getStyle().isBold());
        assertFalse(PerfReportTableRenderer.headerLabel("MAX", PerfReportRows.SortKey.MAX, stale)
                .getStyle().isItalic());
    }

    @Test
    void reportRefreshMarksOrderingStaleUntilManualResort() {
        PerfReportTabState state = new PerfReportTabState();
        PerfReportData refreshed = report(List.of(
                chunk("high-mspt", 0, 0, metric(9.0, 1.0)),
                chunk("high-max", 1, 0, metric(1.0, 9.0))), List.of(), List.of());
        state.rows = PerfReportRows.chunks(refreshed,
                PerfReportRows.SortKey.MSPT, Map.of());
        state.rememberOrder();

        state.refresh();
        assertTrue(state.stale);

        state.resort(PerfReportRows.SortKey.MAX);
        state.rows = PerfReportRows.chunks(refreshed, state.sort, state.stableOrder);
        state.rememberOrder();

        assertFalse(state.stale);
        assertEquals(List.of("high-max [1, 0]", "high-mspt [0, 0]"), labels(state.rows));

        state.refresh();
        assertTrue(state.stale);
        assertEquals(List.of("high-max [1, 0]", "high-mspt [0, 0]"), labels(
                PerfReportRows.chunks(refreshed, state.sort, state.stableOrder)));
    }

    @Test
    void playersExpandIntoAggregatedTicketSources() {
        PerfReportData.OwnerEntry alice = owner("alice", "Alice", metric(5.0, 1.0),
                List.of(ownerSource("self", "Self", null, 271, metric(2.0, 2.0)),
                        ownerSource("loader", "LoaderA", position("test", 8, 64, 9), 25,
                                metric(1.0, 8.0))));
        PerfReportData.OwnerEntry bob = owner("bob", "Bob", metric(2.0, 7.0), List.of());
        PerfReportData report = report(List.of(), List.of(), List.of(alice, bob));

        assertEquals(List.of("Bob(4, 65, 6)", "Alice(4, 65, 6)"),
                labels(PerfReportRows.players(report, PerfReportRows.SortKey.MAX, Set.of(), Map.of())));

        List<PerfReportRow> expanded = PerfReportRows.players(
                report, PerfReportRows.SortKey.MAX, Set.of("owner:PLAYER:alice"), Map.of());
        assertEquals(List.of("Bob(4, 65, 6)", "Alice(4, 65, 6)",
                "LoaderA(8, 64, 9)", "screen.serverwarashi.profiler.source.self"), labels(expanded));
        assertEquals(List.of(0, 0, 1, 1), expanded.stream()
                .map(row -> row.presentation().depth()).toList());
        assertEquals(List.of(296, 296, 25, 271), expanded.stream()
                .map(row -> row.auxiliary().chunkCount()).toList());
        assertEquals("execute in test run tp @s 8 64 9", coordinateCommand(expanded.get(2)));
        List<PerfReportRow> byChunks = PerfReportRows.players(
                report, PerfReportRows.SortKey.CHUNKS, Set.of("owner:PLAYER:alice"), Map.of());
        assertEquals(List.of("screen.serverwarashi.profiler.source.self", "LoaderA(8, 64, 9)"),
                labels(byChunks.stream()
                .filter(row -> row.presentation().depth() == 1).toList()));
    }

    @Test
    void loaderOwnerIsRenderedAsUnnestedTopLevelRow() {
        PerfReportData.OwnerEntry loader = new PerfReportData.OwnerEntry(
                PerfReportData.AttributionKind.LOADER, "loader", "example:chunk_loader", false, 0L,
                position("test", 8, 64, 9), 25, metric(1.0, 2.0),
                List.of(ownerSource("loader", "example:chunk_loader", position("test", 8, 64, 9),
                        25, metric(1.0, 2.0))));

        PerfReportRow row = PerfReportRows.players(report(List.of(), List.of(), List.of(loader)),
                PerfReportRows.SortKey.MSPT, Set.of("owner:LOADER:loader"), Map.of()).getFirst();

        assertEquals("example:chunk_loader(8, 64, 9)", row.label());
        assertTrue(row.capabilities().expansion().isEmpty());
        assertEquals(0, row.presentation().depth());
    }

    @Test
    void reportRefreshKeepsExistingOrderUntilUserResorts() {
        PerfReportData initial = report(List.of(
                chunk("first", 1, 1, metric(5.0, 2.0)),
                chunk("second", 2, 2, metric(1.0, 1.0))), List.of(), List.of());
        List<PerfReportRow> firstRows = PerfReportRows.chunks(
                initial, PerfReportRows.SortKey.MSPT, Map.of());
        Map<String, Integer> stable = new LinkedHashMap<>();
        for (int index = 0; index < firstRows.size(); index++) {
            stable.put(firstRows.get(index).key(), index);
        }

        PerfReportData refreshed = report(List.of(
                chunk("first", 1, 1, metric(1.0, 2.0)),
                chunk("second", 2, 2, metric(9.0, 1.0))), List.of(), List.of());
        assertEquals(List.of("first [1, 1]", "second [2, 2]"), labels(
                PerfReportRows.chunks(refreshed, PerfReportRows.SortKey.MSPT, stable)));
        assertEquals(List.of("second [2, 2]", "first [1, 1]"), labels(
                PerfReportRows.chunks(refreshed, PerfReportRows.SortKey.MSPT, Map.of())));
    }

    @Test
    void coordinateRowsCarryTeleportCommandsAndChunkCounts() {
        PerfReportData report = report(List.of(
                chunk("test:level", 2, -3, 7, 4, metric(1.0, 1.0))),
                List.of(tickable("Cow", metric(1.0, 1.0))), List.of());

        PerfReportRow chunk = PerfReportRows.chunks(report, PerfReportRows.SortKey.MSPT, Map.of()).getFirst();
        PerfReportRow tickable = PerfReportRows.tickables(
                report, PerfReportRows.SortKey.MSPT, Map.of()).getFirst();
        assertEquals(7, chunk.auxiliary().entityCount());
        assertEquals(4, chunk.auxiliary().blockEntityCount());
        assertEquals("[2, -3]", chunk.content().coordinate());
        assertEquals("(1, 2, 3)", tickable.content().coordinate());
        assertEquals("execute in test:level run tp @s 40 ~ -40", coordinateCommand(chunk));
        assertEquals("execute in test run tp @s 1 2 3", coordinateCommand(tickable));
    }

    private static List<String> labels(List<PerfReportRow> rows) {
        return rows.stream().map(PerfReportRow::label).toList();
    }

    private static String coordinateCommand(PerfReportRow row) {
        return row.capabilities().coordinateAction()
                .map(PerfReportRow.CoordinateAction::command).orElse(null);
    }

    private static String tooltip(PerfReportRow row) {
        return row.capabilities().labelTooltip()
                .map(PerfReportRow.LabelTooltip::text).orElse(null);
    }

    private static List<PerfReportRows.SortKey> headerSorts(
            PerfReportGeometry geometry, PerfReportTableRenderer.Layout layout) {
        return PerfReportTableRenderer.sortHeaders(new PerfReportTableRenderer.TableContext(geometry, layout))
                .stream()
                .map(PerfReportTableRenderer.SortHeader::sort)
                .toList();
    }

    private static PerfReportData report(List<PerfReportData.ChunkEntry> chunks,
                                         List<PerfReportData.TickableEntry> tickables,
                                         List<PerfReportData.OwnerEntry> owners) {
        return new PerfReportData(10.0, 1_000, 123_000, 20, 100, 500,
                0, 0, 0, chunks, tickables, owners);
    }

    private static PerfReportData.ChunkEntry chunk(String dimension,
                                                   int x,
                                                   int z,
                                                   PerfReportData.Metric metric) {
        return chunk(dimension, x, z, 0, 0, metric);
    }

    private static PerfReportData.ChunkEntry chunk(String dimension, int x, int z,
                                                   int entities, int blockEntities,
                                                   PerfReportData.Metric metric) {
        return new PerfReportData.ChunkEntry(dimension, x, z, entities, blockEntities,
                List.of(), List.of(), metric);
    }

    private static PerfReportData.TickableEntry tickable(String className, PerfReportData.Metric metric) {
        return new PerfReportData.TickableEntry(TickType.ENTITY, "test", "example." + className,
                "", TickDetailKind.NONE, 1, true, 1, 2, 3, false, "", metric);
    }

    private static PerfReportData.OwnerEntry owner(String id,
                                                   String name,
                                                   PerfReportData.Metric metric,
                                                   List<PerfReportData.OwnerSourceEntry> sources) {
        return new PerfReportData.OwnerEntry(PerfReportData.AttributionKind.PLAYER,
                id, name, false, 60_000L, position("test", 4, 65, 6), 296, metric, sources);
    }

    private static PerfReportData.OwnerSourceEntry ownerSource(
            String id, String name, PerfReportData.Position position, int chunks, PerfReportData.Metric metric) {
        return new PerfReportData.OwnerSourceEntry(id, name, position, chunks, metric);
    }

    private static PerfReportData.Position position(String dimension, int x, int y, int z) {
        return new PerfReportData.Position(dimension, x, y, z);
    }

    private static PerfReportData.Metric metric(double mspt, double max) {
        return new PerfReportData.Metric(mspt, max, 10);
    }

    private static PerfReportData.Metric metric(double mspt, double max, long samples) {
        return new PerfReportData.Metric(mspt, max, samples);
    }
}
