package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import com.moepus.serverwarashi.common.report.ReportButtons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PerfReportRows {
    private PerfReportRows() {
    }

    static List<PerfReportRow> chunks(PerfReportData report, SortKey sort,
                                     Set<String> expanded, Map<String, Integer> stableOrder) {
        List<ChunkRow> chunks = report.chunks().stream()
                .map(entry -> new ChunkRow(entry, chunkRow(entry, expanded)))
                .sorted(Comparator.comparing(ChunkRow::row, comparator(sort, stableOrder)))
                .toList();
        List<PerfReportRow> rows = new ArrayList<>();
        chunks.forEach(chunk -> appendChunk(rows, chunk));
        return List.copyOf(rows);
    }

    static List<PerfReportRow> chunks(
            PerfReportData report, SortKey sort, Map<String, Integer> stableOrder) {
        return chunks(report, sort, Set.of(), stableOrder);
    }

    static List<PerfReportRow> tickables(
            PerfReportData report, SortKey sort, Map<String, Integer> stableOrder) {
        List<PerfReportRow> rows = report.tickables().stream()
                .filter(entry -> entry.type() != TickType.GC)
                .map(PerfReportRows::tickableRow).toList();
        List<PerfReportRow> result = new ArrayList<>(rows.size() + 1);
        report.tickables().stream().filter(entry -> entry.type() == TickType.GC)
                .map(PerfReportRows::tickableRow).findFirst().ifPresent(result::add);
        result.addAll(sortRows(rows, sort, stableOrder));
        return List.copyOf(result);
    }

    static List<PerfReportRow> players(PerfReportData report,
                                      SortKey sort,
                                      Set<String> expanded,
                                      Map<String, Integer> stableOrder) {
        List<OwnerRow> owners = report.owners().stream()
                .map(owner -> new OwnerRow(owner, ownerRow(owner, expanded)))
                .sorted(Comparator.comparing(OwnerRow::row, comparator(sort, stableOrder)))
                .toList();
        List<PerfReportRow> rows = new ArrayList<>();
        for (OwnerRow owner : owners) {
            appendOwner(rows, owner, sort, stableOrder);
        }
        return List.copyOf(rows);
    }

    static String metric(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void appendOwner(List<PerfReportRow> rows,
                                    OwnerRow ownerRow,
                                    SortKey sort,
                                    Map<String, Integer> stableOrder) {
        PerfReportData.OwnerEntry owner = ownerRow.owner();
        String key = ownerKey(owner);
        rows.add(ownerRow.row());
        if (!isExpanded(ownerRow.row())) {
            return;
        }
        sortRows(owner.sources().stream().map(entry -> ownerSourceRow(key, entry)).toList(), sort, stableOrder).stream()
                .forEach(rows::add);
    }

    private static PerfReportRow ownerRow(PerfReportData.OwnerEntry owner, Set<String> expanded) {
        String key = ownerKey(owner);
        boolean expandable = owner.kind() != PerfReportData.AttributionKind.LOADER && !owner.sources().isEmpty();
        PerfReportRow.Capabilities capabilities = PerfReportRow.Capabilities.none()
                .withCoordinateAction(teleportCommand(owner.position()));
        if (expandable) {
            capabilities = capabilities.withExpansion(key, expanded.contains(key));
        }
        Optional<PerfReportRow.PlayerStatus> playerStatus =
                owner.kind() == PerfReportData.AttributionKind.PLAYER
                        ? Optional.of(new PerfReportRow.PlayerStatus(
                        owner.online(), owner.lastLoginEpochMillis())) : Optional.empty();
        return PerfReportRow.metric(key,
                new PerfReportRow.Content(ownerLabel(owner), positionText(owner.position())),
                new PerfReportRow.MetricOptions(
                        new PerfReportRow.OwnerCells(owner.chunkCount(), playerStatus), owner.metric(),
                        new PerfReportRow.Presentation(0, capabilities)));
    }

    private static PerfReportRow chunkRow(PerfReportData.ChunkEntry entry, Set<String> expanded) {
        String key = chunkKey(entry);
        String command = ReportButtons.chunkTeleportCommand(entry.dimension(), entry.chunkX(), entry.chunkZ());
        boolean expandable = !entry.topEntities().isEmpty() || !entry.topBlockEntities().isEmpty();
        PerfReportRow.Capabilities capabilities = PerfReportRow.Capabilities.none()
                .withCoordinateAction(command);
        if (expandable) {
            capabilities = capabilities.withExpansion(key, expanded.contains(key));
        }
        return PerfReportRow.metric(key,
                new PerfReportRow.Content(
                        entry.dimension() + " ", chunkPosition(entry.chunkX(), entry.chunkZ())),
                new PerfReportRow.MetricOptions(
                        new PerfReportRow.ChunkCells(entry.entityCount(), entry.blockEntityCount()),
                        entry.metric(), new PerfReportRow.Presentation(0, capabilities)));
    }

    private static void appendChunk(List<PerfReportRow> rows, ChunkRow chunk) {
        rows.add(chunk.row());
        if (!isExpanded(chunk.row())) {
            return;
        }
        chunk.entry().topEntities().forEach(count -> rows.add(objectCountRow(chunk, count, true)));
        chunk.entry().topBlockEntities().forEach(count -> rows.add(objectCountRow(chunk, count, false)));
    }

    private static PerfReportRow objectCountRow(
            ChunkRow chunk, PerfReportData.ObjectCount count, boolean entity) {
        String type = entity ? "entity" : "block_entity";
        String key = chunk.row().key() + ":" + type + ":" + count.className() + ":" + count.detailId();
        String coordinate = "(" + count.x() + ", " + count.y() + ", " + count.z() + ")";
        String command = ReportButtons.teleportCommand(
                chunk.entry().dimension(), count.x(), count.y(), count.z());
        return PerfReportRow.summary(key,
                new PerfReportRow.Content(PerfDisplayText.objectName(
                        count.detailKind(), count.className(), count.detailId()), coordinate),
                new PerfReportRow.SummaryOptions(
                        new PerfReportRow.ChunkCells(
                                entity ? count.count() : 0, entity ? 0 : count.count()),
                        new PerfReportRow.Presentation(1, PerfReportRow.Capabilities.none()
                                .withCoordinateAction(command))));
    }

    private static PerfReportRow tickableRow(PerfReportData.TickableEntry entry) {
        String label = tickableLabel(entry);
        String coordinate = tickablePosition(entry);
        String prefix = coordinate.isEmpty() ? label : label.substring(0, label.length() - coordinate.length());
        PerfReportRow.Capabilities capabilities = PerfReportRow.Capabilities.none()
                .withCoordinateAction(teleportCommand(entry));
        if (entry.type() == TickType.HANDLER) {
            capabilities = capabilities.withLabelTooltip(entry.eventLoop());
        }
        return PerfReportRow.metric(tickableKey(entry),
                new PerfReportRow.Content(prefix, coordinate),
                new PerfReportRow.MetricOptions(
                        new PerfReportRow.TickableCells(typeLabel(entry.type())), entry.metric(),
                        new PerfReportRow.Presentation(0, capabilities)));
    }

    private static PerfReportRow ownerSourceRow(
            String ownerKey, PerfReportData.OwnerSourceEntry entry) {
        String key = ownerKey + ":source:" + entry.id();
        return PerfReportRow.metric(key,
                new PerfReportRow.Content(sourceName(entry), positionText(entry.position())),
                new PerfReportRow.MetricOptions(
                        new PerfReportRow.OwnerCells(entry.chunkCount(), Optional.empty()), entry.metric(),
                        new PerfReportRow.Presentation(1, PerfReportRow.Capabilities.none()
                                .withCoordinateAction(teleportCommand(entry.position())))));
    }

    private static String tickableLabel(PerfReportData.TickableEntry entry) {
        if (entry.type() == TickType.HANDLER) {
            return entry.className();
        }
        if (entry.type() == TickType.GC) {
            return PerfDisplayText.translated("screen.serverwarashi.profiler.tickable.gc");
        }
        String name = PerfDisplayText.objectName(
                entry.detailKind(), entry.className(), entry.detailId());
        if (entry.type() == TickType.ENTITY) {
            name += "#" + entry.entityId();
        }
        if (entry.type() == TickType.TASK) {
            if (entry.eventLoop().isEmpty()) {
                return PerfDisplayText.translated("screen.serverwarashi.profiler.tickable.unknown");
            }
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
            case HANDLER -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.handler");
            case GC -> PerfDisplayText.translated("screen.serverwarashi.profiler.type.gc");
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
            case HANDLER -> "handler:" + entry.eventLoop();
            case GC -> "gc:rolling-1s";
        };
    }

    private static String ownerKey(PerfReportData.OwnerEntry owner) {
        return "owner:" + owner.kind() + ":" + owner.id();
    }

    private static boolean isExpanded(PerfReportRow row) {
        return row.capabilities().expansion()
                .map(PerfReportRow.Expansion::expanded)
                .orElse(false);
    }

    private static List<PerfReportRow> sortRows(
            List<PerfReportRow> rows, SortKey sort, Map<String, Integer> stableOrder) {
        return rows.stream().sorted(comparator(sort, stableOrder)).toList();
    }

    private static Comparator<PerfReportRow> comparator(
            SortKey sort, Map<String, Integer> stableOrder) {
        Comparator<PerfReportRow> requested = Comparator.comparingDouble(sort::value)
                .reversed().thenComparing(PerfReportRow::label);
        return Comparator.comparingInt((PerfReportRow row) ->
                        stableOrder.getOrDefault(row.key(), Integer.MAX_VALUE))
                .thenComparing(requested);
    }

    enum SortKey {
        MSPT(row -> row.metric().mspt()),
        MAX(row -> row.metric().max()),
        SAMPLES(row -> row.metric().samples()),
        CHUNKS(row -> row.auxiliary().chunkCount()),
        ENTITIES(row -> row.auxiliary().entityCount()),
        BLOCK_ENTITIES(row -> row.auxiliary().blockEntityCount());

        private final java.util.function.ToDoubleFunction<PerfReportRow> value;

        SortKey(java.util.function.ToDoubleFunction<PerfReportRow> value) {
            this.value = value;
        }

        double value(PerfReportRow row) {
            return value.applyAsDouble(row);
        }
    }

    private record OwnerRow(PerfReportData.OwnerEntry owner, PerfReportRow row) {
    }

    private record ChunkRow(PerfReportData.ChunkEntry entry, PerfReportRow row) {
    }
}
