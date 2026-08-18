package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.ChunkPerformanceRisk;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.weakload.ChunkEntityCollector;
import com.moepus.serverwarashi.common.modules.weakload.ChunkEntityCollector.ChunkEntitySummary;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkClearJobs;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PerfChunkEntityScanQueue {
    private static final int TOP_TYPE_LIMIT = 3;
    private final LinkedHashSet<ChunkKey> deletionQueue = new LinkedHashSet<>();
    private final LinkedHashSet<ChunkKey> reportQueue = new LinkedHashSet<>();
    private final Map<ChunkKey, ChunkEntitySummary> cache = new HashMap<>();
    private Set<ChunkKey> currentKeys = Set.of();
    private Set<ChunkKey> currentRedKeys = Set.of();
    private final Set<ChunkKey> forcedCurrentKeys = new HashSet<>();
    private ReportVersion version;
    private PerfReportData sourceReport;
    private PerfReportData materializedReport;

    PerfReportData accept(PerfReportData report) {
        if (report == null) {
            return null;
        }
        ReportVersion nextVersion = ReportVersion.from(report);
        sourceReport = report;
        if (!nextVersion.equals(version)) {
            version = nextVersion;
            currentKeys = indexKeys(report);
            currentRedKeys = indexRedKeys(report);
            forcedCurrentKeys.clear();
            reportQueue.clear();
            reportQueue.addAll(currentRedKeys);
            retainCurrentCache();
        }
        materializedReport = materialize();
        return materializedReport;
    }

    void prioritize(List<WeakChunkClearJobs.CompletedChunk> completed) {
        for (WeakChunkClearJobs.CompletedChunk chunk : completed) {
            ChunkKey key = new ChunkKey(chunk.dimension(), chunk.chunkPos());
            deletionQueue.add(key);
            reportQueue.remove(key);
        }
    }

    ScanBatch scan(MinecraftServer server) {
        return scan((key, deletion) -> scan(server, key, deletion));
    }

    ScanBatch scan(EntityScanner scanner) {
        List<TrackedCorrection> trackedCorrections = new ArrayList<>();
        Set<ChunkKey> scanned = new HashSet<>();
        drainDeletionQueue(scanner, trackedCorrections, scanned);
        drainReportQueue(scanner, trackedCorrections, scanned);
        PerfReportData update = updatedReport();
        return new ScanBatch(scanned.size(), trackedCorrections, update);
    }

    void clear() {
        deletionQueue.clear();
        reportQueue.clear();
        cache.clear();
        currentKeys = Set.of();
        currentRedKeys = Set.of();
        forcedCurrentKeys.clear();
        version = null;
        sourceReport = null;
        materializedReport = null;
    }

    private void drainDeletionQueue(EntityScanner scanner,
                                    List<TrackedCorrection> corrections,
                                    Set<ChunkKey> scanned) {
        while (!deletionQueue.isEmpty()) {
            ChunkKey key = removeFirst(deletionQueue);
            ScanValue value = scanner.scan(key, true);
            scanned.add(key);
            cache.put(key, value.ticking());
            if (currentKeys.contains(key)) {
                forcedCurrentKeys.add(key);
            }
            corrections.add(new TrackedCorrection(key, value.tracked()));
        }
    }

    private void drainReportQueue(EntityScanner scanner,
                                  List<TrackedCorrection> corrections,
                                  Set<ChunkKey> scanned) {
        while (!reportQueue.isEmpty()) {
            ChunkKey key = removeFirst(reportQueue);
            if (!scanned.add(key)) {
                continue;
            }
            ScanValue value = scanner.scan(key, false);
            cache.put(key, value.ticking());
            corrections.add(new TrackedCorrection(key, value.tracked()));
        }
    }

    private static ScanValue scan(MinecraftServer server, ChunkKey key, boolean deletion) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null) {
            return new ScanValue(emptySummary(key), null);
        }
        ChunkEntityCollector.VisibilitySummaries summaries =
                ChunkEntityCollector.inspectByVisibility(level, key.chunkPos());
        return new ScanValue(summaries.ticking(), summaries.tracked().toWeakEntry());
    }

    private PerfReportData updatedReport() {
        PerfReportData next = materialize();
        if (next == null || next.equals(materializedReport)) {
            return null;
        }
        materializedReport = next;
        return next;
    }

    private PerfReportData materialize() {
        if (sourceReport == null) {
            return null;
        }
        List<PerfReportData.ChunkEntry> chunks = sourceReport.chunks().stream()
                .map(this::materializeChunk)
                .toList();
        return sourceReport.withChunks(chunks);
    }

    private PerfReportData.ChunkEntry materializeChunk(PerfReportData.ChunkEntry chunk) {
        ChunkKey key = key(chunk);
        if (key == null || (!currentRedKeys.contains(key) && !forcedCurrentKeys.contains(key))) {
            return chunk;
        }
        ChunkEntitySummary summary = cache.get(key);
        return summary == null ? chunk : chunk.withEntities(
                summary.entityCount(), objectCounts(summary));
    }

    private static List<PerfReportData.ObjectCount> objectCounts(ChunkEntitySummary summary) {
        return summary.topTypes(TOP_TYPE_LIMIT).stream().map(entry -> {
            WeakEntityType type = entry.getKey();
            ChunkEntityCollector.TypeEstimate estimate = entry.getValue();
            TickDetailKind detailKind = type.kind() == WeakEntityType.Kind.ITEM
                    ? TickDetailKind.ITEM : TickDetailKind.ENTITY;
            return new PerfReportData.ObjectCount("", type.id(), detailKind, estimate.count(),
                    estimate.x(), estimate.y(), estimate.z());
        }).toList();
    }

    private static Set<ChunkKey> indexKeys(PerfReportData report) {
        Set<ChunkKey> result = new HashSet<>();
        report.chunks().forEach(chunk -> addKey(result, chunk));
        return Set.copyOf(result);
    }

    private static Set<ChunkKey> indexRedKeys(PerfReportData report) {
        Set<ChunkKey> result = new LinkedHashSet<>();
        report.chunks().stream()
                .filter(chunk -> ChunkPerformanceRisk.isRedOrHotter(chunk.metric()))
                .forEach(chunk -> addKey(result, chunk));
        return Set.copyOf(result);
    }

    private static void addKey(Set<ChunkKey> target, PerfReportData.ChunkEntry chunk) {
        ChunkKey key = key(chunk);
        if (key != null) {
            target.add(key);
        }
    }

    private static ChunkKey key(PerfReportData.ChunkEntry chunk) {
        ResourceLocation id = ResourceLocation.tryParse(chunk.dimension());
        return id == null ? null : new ChunkKey(
                ResourceKey.create(Registries.DIMENSION, id),
                ChunkPos.asLong(chunk.chunkX(), chunk.chunkZ()));
    }

    private void retainCurrentCache() {
        Set<ChunkKey> retained = new HashSet<>(currentKeys);
        retained.addAll(deletionQueue);
        cache.keySet().retainAll(retained);
    }

    private static ChunkEntitySummary emptySummary(ChunkKey key) {
        ChunkPos position = new ChunkPos(key.chunkPos());
        return new ChunkEntitySummary(position.x, position.z, 0, Map.of());
    }

    private static <T> T removeFirst(LinkedHashSet<T> values) {
        T first = values.getFirst();
        values.remove(first);
        return first;
    }

    record ChunkKey(ResourceKey<Level> dimension, long chunkPos) {
    }

    record TrackedCorrection(ChunkKey key, WeakChunkSnapshot.ChunkEntry entry) {
    }

    record ScanBatch(int scannedChunks,
                     List<TrackedCorrection> trackedCorrections,
                     PerfReportData reportUpdate) {
        ScanBatch {
            trackedCorrections = List.copyOf(trackedCorrections);
        }
    }

    record ScanValue(ChunkEntitySummary ticking, WeakChunkSnapshot.ChunkEntry tracked) {
    }

    private record ReportVersion(long reportId, long tickCount, long generatedAt) {
        private static ReportVersion from(PerfReportData report) {
            return new ReportVersion(
                    report.reportId(), report.tickCount(), report.generatedAtEpochMillis());
        }
    }

    @FunctionalInterface
    interface EntityScanner {
        ScanValue scan(ChunkKey key, boolean deletion);
    }
}
