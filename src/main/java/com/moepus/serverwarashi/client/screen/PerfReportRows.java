package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import com.moepus.serverwarashi.common.report.ReportButtons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PerfReportRows {
    private static final PerfReportData.Metric EMPTY_METRIC = new PerfReportData.Metric(0.0D, 0.0D, 0L);

    private PerfReportRows() {
    }

    static List<Row> chunks(PerfReportData report, SortKey sort,
                            Set<String> expanded, Map<String, Integer> stableOrder) {
        List<ChunkRow> chunks = report.chunks().stream()
                .map(entry -> new ChunkRow(entry, chunkRow(entry, expanded)))
                .sorted(Comparator.comparing(ChunkRow::row, comparator(sort, stableOrder)))
                .toList();
        List<Row> rows = new ArrayList<>();
        chunks.forEach(chunk -> appendChunk(rows, chunk));
        return List.copyOf(rows);
    }

    static List<Row> chunks(PerfReportData report, SortKey sort, Map<String, Integer> stableOrder) {
        return chunks(report, sort, Set.of(), stableOrder);
    }

    static List<Row> tickables(PerfReportData report, SortKey sort, Map<String, Integer> stableOrder) {
        return sortRows(report.tickables().stream().map(PerfReportRows::tickableRow).toList(), sort, stableOrder);
    }

    static List<Row> players(PerfReportData report,
                             SortKey sort,
                             Set<String> expanded,
                             Map<String, Integer> stableOrder) {
        List<OwnerRow> owners = report.owners().stream()
                .map(owner -> new OwnerRow(owner, ownerRow(owner, expanded)))
                .sorted(Comparator.comparing(OwnerRow::row, comparator(sort, stableOrder)))
                .toList();
        List<Row> rows = new ArrayList<>();
        for (OwnerRow owner : owners) {
            appendOwner(rows, owner, sort, stableOrder);
        }
        return List.copyOf(rows);
    }

    static String metric(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void appendOwner(List<Row> rows,
                                    OwnerRow ownerRow,
                                    SortKey sort,
                                    Map<String, Integer> stableOrder) {
        PerfReportData.OwnerEntry owner = ownerRow.owner();
        String key = ownerKey(owner);
        rows.add(ownerRow.row());
        if (!ownerRow.row().expanded()) {
            return;
        }
        sortRows(owner.sources().stream().map(entry -> ownerSourceRow(key, entry)).toList(), sort, stableOrder).stream()
                .forEach(rows::add);
    }

    private static Row ownerRow(PerfReportData.OwnerEntry owner, Set<String> expanded) {
        String key = ownerKey(owner);
        boolean expandable = owner.kind() != PerfReportData.AttributionKind.LOADER && !owner.sources().isEmpty();
        return Row.owner(key, ownerLabel(owner), owner, expandable, expandable && expanded.contains(key));
    }

    private static Row chunkRow(PerfReportData.ChunkEntry entry, Set<String> expanded) {
        String key = chunkKey(entry);
        String command = ReportButtons.chunkTeleportCommand(entry.dimension(), entry.chunkX(), entry.chunkZ());
        boolean expandable = !entry.topEntities().isEmpty() || !entry.topBlockEntities().isEmpty();
        return Row.chunk(key, entry.dimension() + " ", chunkPosition(entry.chunkX(), entry.chunkZ()),
                entry.entityCount(), entry.blockEntityCount(), entry.metric(), command,
                expandable, expandable && expanded.contains(key));
    }

    private static void appendChunk(List<Row> rows, ChunkRow chunk) {
        rows.add(chunk.row());
        if (!chunk.row().expanded()) {
            return;
        }
        chunk.entry().topEntities().forEach(count -> rows.add(objectCountRow(chunk, count, true)));
        chunk.entry().topBlockEntities().forEach(count -> rows.add(objectCountRow(chunk, count, false)));
    }

    private static Row objectCountRow(ChunkRow chunk, PerfReportData.ObjectCount count, boolean entity) {
        String type = entity ? "entity" : "block_entity";
        String key = chunk.row().key() + ":" + type + ":" + count.className() + ":" + count.detailId();
        String coordinate = "(" + count.x() + ", " + count.y() + ", " + count.z() + ")";
        String command = ReportButtons.teleportCommand(
                chunk.entry().dimension(), count.x(), count.y(), count.z());
        return Row.summary(key, PerfDisplayText.objectName(
                        count.detailKind(), count.className(), count.detailId()),
                entity ? count.count() : 0, entity ? 0 : count.count(), coordinate, command);
    }

    private static Row tickableRow(PerfReportData.TickableEntry entry) {
        String label = tickableLabel(entry);
        String coordinate = tickablePosition(entry);
        String prefix = coordinate.isEmpty() ? label : label.substring(0, label.length() - coordinate.length());
        return Row.leaf(tickableKey(entry), prefix, coordinate, typeLabel(entry.type()),
                0, 0, 0, entry.metric(), 0, teleportCommand(entry));
    }

    private static Row ownerSourceRow(String ownerKey, PerfReportData.OwnerSourceEntry entry) {
        String key = ownerKey + ":source:" + entry.id();
        return Row.leaf(key, sourceName(entry), positionText(entry.position()), "",
                0, 0, entry.chunkCount(), entry.metric(), 1, teleportCommand(entry.position()));
    }

    private static String tickableLabel(PerfReportData.TickableEntry entry) {
        String name = PerfDisplayText.objectName(
                entry.detailKind(), entry.className(), entry.detailId());
        if (entry.type() == TickType.ENTITY) {
            name += "#" + entry.entityId();
        }
        if (entry.type() == TickType.TASK) {
            return name + " @ " + entry.eventLoop();
        }
        if (!entry.positioned()) {
            return name + " @ " + entry.dimension();
        }
        return name + " @ " + entry.dimension() + " " + tickablePosition(entry);
    }

    private static String tickablePosition(PerfReportData.TickableEntry entry) {
        if (!entry.positioned()) {
            return "";
        }
        return entry.chunkPosition()
                ? chunkPosition(entry.x(), entry.z())
                : "(" + entry.x() + ", " + entry.y() + ", " + entry.z() + ")";
    }

    private static String typeLabel(TickType type) {
        return switch (type) {
            case CHUNK -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.chunk");
            case ENTITY -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.entity");
            case BLOCK_ENTITY -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.block_entity");
            case TASK -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.task");
        };
    }

    private static String teleportCommand(PerfReportData.TickableEntry entry) {
        if (!entry.positioned()) {
            return null;
        }
        return entry.chunkPosition()
                ? ReportButtons.chunkTeleportCommand(entry.dimension(), entry.x(), entry.z())
                : ReportButtons.teleportCommand(entry.dimension(), entry.x(), entry.y(), entry.z());
    }

    private static String positionText(PerfReportData.Position position) {
        return position == null ? "" : "(" + position.x() + ", " + position.y() + ", " + position.z() + ")";
    }

    private static String teleportCommand(PerfReportData.Position position) {
        return position == null ? null : ReportButtons.teleportCommand(
                position.dimension(), position.x(), position.y(), position.z());
    }

    private static String ownerLabel(PerfReportData.OwnerEntry owner) {
        return switch (owner.kind()) {
            case PLAYER -> owner.name();
            case LOADER -> PerfDisplayText.resource(owner.name());
            case SYSTEM -> PerfDisplayText.translated("screen.serverwarashi.profiler.owner.system");
            case UNKNOWN -> PerfDisplayText.translated("screen.serverwarashi.profiler.owner.unknown");
        };
    }

    private static String sourceName(PerfReportData.OwnerSourceEntry entry) {
        if ("self".equals(entry.id())) {
            return PerfDisplayText.translated("screen.serverwarashi.profiler.source.self");
        }
        if ("Unknown".equals(entry.name())) {
            return PerfDisplayText.translated("screen.serverwarashi.profiler.source.unknown");
        }
        return PerfDisplayText.resource(entry.name());
    }

    private static String chunkPosition(int chunkX, int chunkZ) {
        return "[" + chunkX + ", " + chunkZ + "]";
    }

    private static String chunkKey(PerfReportData.ChunkEntry entry) {
        return chunkKey(entry.dimension(), entry.chunkX(), entry.chunkZ());
    }

    private static String chunkKey(String dimension, int x, int z) {
        return "chunk:" + dimension + ":" + x + ":" + z;
    }

    private static String tickableKey(PerfReportData.TickableEntry entry) {
        return switch (entry.type()) {
            case ENTITY -> "entity:" + entry.dimension() + ":" + entry.entityId() + ":" + entry.className()
                    + ":" + entry.detailId();
            case BLOCK_ENTITY -> "be:" + entry.dimension() + ":" + entry.x() + ":" + entry.y() + ":"
                    + entry.z() + ":" + entry.className();
            case CHUNK -> chunkKey(entry.dimension(), entry.x(), entry.z());
            case TASK -> "task:" + entry.eventLoop() + ":" + entry.className();
        };
    }

    private static String ownerKey(PerfReportData.OwnerEntry owner) {
        return "owner:" + owner.kind() + ":" + owner.id();
    }

    private static List<Row> sortRows(List<Row> rows, SortKey sort, Map<String, Integer> stableOrder) {
        return rows.stream().sorted(comparator(sort, stableOrder)).toList();
    }

    private static Comparator<Row> comparator(SortKey sort, Map<String, Integer> stableOrder) {
        Comparator<Row> requested = Comparator.comparingDouble(sort::value).reversed().thenComparing(Row::label);
        return Comparator.comparingInt((Row row) -> stableOrder.getOrDefault(row.key(), Integer.MAX_VALUE))
                .thenComparing(requested);
    }

    enum SortKey {
        MSPT(row -> row.metric().mspt()),
        MAX(row -> row.metric().max()),
        SAMPLES(row -> row.metric().samples()),
        CHUNKS(Row::chunkCount),
        ENTITIES(Row::entityCount),
        BLOCK_ENTITIES(Row::blockEntityCount);

        private final java.util.function.ToDoubleFunction<Row> value;

        SortKey(java.util.function.ToDoubleFunction<Row> value) {
            this.value = value;
        }

        double value(Row row) {
            return value.applyAsDouble(row);
        }
    }

    private record OwnerRow(PerfReportData.OwnerEntry owner, Row row) {
    }

    private record ChunkRow(PerfReportData.ChunkEntry entry, Row row) {
    }

    record Row(String key,
               String labelPrefix,
               String coordinate,
               String type,
               int entityCount,
               int blockEntityCount,
               int chunkCount,
               boolean playerOwner,
               boolean online,
               long lastLoginEpochMillis,
               PerfReportData.Metric metric,
               int depth,
               boolean expandable,
               boolean expanded,
               String expansionKey,
               String teleportCommand,
               boolean summary) {
        static Row leaf(String key, String labelPrefix, String coordinate, String type,
                        int entityCount, int blockEntityCount, int chunkCount,
                        PerfReportData.Metric metric, int depth, String teleportCommand) {
            return new Row(key, labelPrefix, coordinate, type, entityCount, blockEntityCount, chunkCount,
                    false, false, 0L, metric, depth, false, false, null, teleportCommand, false);
        }

        static Row chunk(String key, String labelPrefix, String coordinate,
                         int entityCount, int blockEntityCount, PerfReportData.Metric metric,
                         String teleportCommand, boolean expandable, boolean expanded) {
            return new Row(key, labelPrefix, coordinate, "", entityCount, blockEntityCount,
                    0, false, false, 0L, metric, 0, expandable, expanded, key, teleportCommand, false);
        }

        static Row summary(String key, String label, int entityCount, int blockEntityCount,
                           String coordinate, String teleportCommand) {
            return new Row(key, label, coordinate, "", entityCount, blockEntityCount,
                    0, false, false, 0L, EMPTY_METRIC, 1, false, false, null, teleportCommand, true);
        }

        static Row owner(String key, String label, PerfReportData.OwnerEntry owner,
                         boolean expandable, boolean expanded) {
            boolean player = owner.kind() == PerfReportData.AttributionKind.PLAYER;
            return new Row(key, label, positionText(owner.position()), "", 0, 0, owner.chunkCount(),
                    player, owner.online(), owner.lastLoginEpochMillis(), owner.metric(), 0,
                    expandable, expanded, key, PerfReportRows.teleportCommand(owner.position()), false);
        }

        String label() {
            return labelPrefix + coordinate;
        }
    }
}
