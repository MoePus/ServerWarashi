package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record PerfReportData(long reportId,
                             double serverMspt,
                             long lastTickGcMillis,
                             long elapsedMillis,
                             long generatedAtEpochMillis,
                             long tickCount,
                             long totalSamples,
                             int intervalMicros,
                             int omittedChunks,
                             int omittedTickables,
                             int omittedOwnerSources,
                             int omittedLoaderSources,
                             List<ChunkEntry> chunks,
                             List<TickableEntry> tickables,
                             List<OwnerEntry> owners,
                             List<LoaderSourceEntry> loaderSources) {
    public PerfReportData {
        if (reportId < 0L) {
            throw new IllegalArgumentException("Report ID must be non-negative");
        }
        if (lastTickGcMillis < 0L) {
            throw new IllegalArgumentException("Last tick GC time must be non-negative");
        }
        if (omittedChunks < 0 || omittedTickables < 0
                || omittedOwnerSources < 0 || omittedLoaderSources < 0) {
            throw new IllegalArgumentException("Omitted report counts must be non-negative");
        }
        chunks = List.copyOf(chunks);
        tickables = List.copyOf(tickables);
        owners = List.copyOf(owners);
        loaderSources = List.copyOf(loaderSources);
    }

    public PerfReportData(double serverMspt,
                          long elapsedMillis,
                          long generatedAtEpochMillis,
                          long tickCount,
                          long totalSamples,
                          int intervalMicros,
                          int omittedChunks,
                          int omittedTickables,
                          int omittedOwnerSources,
                          int omittedLoaderSources,
                          List<ChunkEntry> chunks,
                          List<TickableEntry> tickables,
                          List<OwnerEntry> owners,
                          List<LoaderSourceEntry> loaderSources) {
        this(0L, serverMspt, 0L, elapsedMillis, generatedAtEpochMillis, tickCount, totalSamples,
                intervalMicros, omittedChunks, omittedTickables, omittedOwnerSources,
                omittedLoaderSources, chunks, tickables, owners, loaderSources);
    }

    public PerfReportData(long reportId,
                          double serverMspt,
                          long elapsedMillis,
                          long generatedAtEpochMillis,
                          long tickCount,
                          long totalSamples,
                          int intervalMicros,
                          int omittedChunks,
                          int omittedTickables,
                          int omittedOwnerSources,
                          int omittedLoaderSources,
                          List<ChunkEntry> chunks,
                          List<TickableEntry> tickables,
                          List<OwnerEntry> owners,
                          List<LoaderSourceEntry> loaderSources) {
        this(reportId, serverMspt, 0L, elapsedMillis, generatedAtEpochMillis, tickCount, totalSamples,
                intervalMicros, omittedChunks, omittedTickables, omittedOwnerSources,
                omittedLoaderSources, chunks, tickables, owners, loaderSources);
    }

    public PerfReportData(double serverMspt,
                          long elapsedMillis,
                          long generatedAtEpochMillis,
                          long tickCount,
                          long totalSamples,
                          int intervalMicros,
                          int omittedChunks,
                          int omittedTickables,
                          int omittedOwnerSources,
                          List<ChunkEntry> chunks,
                          List<TickableEntry> tickables,
                          List<OwnerEntry> owners) {
        this(0L, serverMspt, 0L, elapsedMillis, generatedAtEpochMillis, tickCount, totalSamples, intervalMicros,
                omittedChunks, omittedTickables, omittedOwnerSources, 0,
                chunks, tickables, owners, List.of());
    }

    public static PerfReportData empty(int intervalMicros) {
        return new PerfReportData(0L, 0.0D, 0L, 0L, 0L, 0L, 0L, intervalMicros,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
    }

    public PerfReportData withLoaderSources(List<LoaderSourceEntry> nextLoaderSources) {
        return loaderSources.equals(nextLoaderSources) ? this : new PerfReportData(
                reportId, serverMspt, lastTickGcMillis, elapsedMillis, generatedAtEpochMillis,
                tickCount, totalSamples,
                intervalMicros, omittedChunks, omittedTickables, omittedOwnerSources,
                omittedLoaderSources, chunks, tickables, owners, nextLoaderSources);
    }

    public record Metric(double mspt, double max, long samples) {
    }

    public record Position(String dimension, int x, int y, int z) {
    }

    public record LoaderSourceEntry(String dimension,
                                    String sourceId,
                                    String loaderName,
                                    LoaderPosition position,
                                    int omittedRegions,
                                    List<LoaderRegion> regions,
                                    LoaderControl control) {
        public LoaderSourceEntry {
            if (omittedRegions < 0) {
                throw new IllegalArgumentException("Omitted loader region count must be non-negative");
            }
            regions = List.copyOf(regions);
        }

        public LoaderSourceEntry(String dimension, String sourceId, String loaderName,
                                 LoaderPosition position, int omittedRegions, List<LoaderRegion> regions) {
            this(dimension, sourceId, loaderName, position, omittedRegions, regions, null);
        }

        public LoaderSourceEntry withControl(LoaderControl nextControl) {
            return Objects.equals(control, nextControl) ? this : new LoaderSourceEntry(
                    dimension, sourceId, loaderName, position, omittedRegions, regions, nextControl);
        }
    }

    public record LoaderControl(ResourceLocation controllerId,
                                BlockPos ownerPos,
                                boolean manuallyFrozen,
                                boolean working) {
        public LoaderControl {
            controllerId = Objects.requireNonNull(controllerId, "controllerId");
            ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
        }
    }

    public record LoaderPosition(int x, int y, int z) {
    }

    public record LoaderRegion(int minChunkX, int minChunkZ, int width, int height) {
        public LoaderRegion {
            if (width <= 0 || height <= 0
                    || width > PerfReportLimits.MAX_LOADER_REGION_SPAN
                    || height > PerfReportLimits.MAX_LOADER_REGION_SPAN) {
                throw new IllegalArgumentException("Invalid loader region dimensions");
            }
            long maxX = (long) minChunkX + width - 1L;
            long maxZ = (long) minChunkZ + height - 1L;
            if (maxX > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Loader region endpoint overflow");
            }
        }
    }

    public record ChunkEntry(String dimension,
                             int chunkX,
                             int chunkZ,
                             int entityCount,
                             int blockEntityCount,
                             List<ObjectCount> topEntities,
                             List<ObjectCount> topBlockEntities,
                             Metric metric) {
        public ChunkEntry {
            topEntities = List.copyOf(topEntities);
            topBlockEntities = List.copyOf(topBlockEntities);
        }
    }

    public record ObjectCount(String className,
                              String detailId,
                              TickDetailKind detailKind,
                              int count,
                              int x,
                              int y,
                              int z) {
        public ObjectCount {
            detailId = detailId == null ? "" : detailId;
        }
    }

    public record TickableEntry(TickType type,
                                String dimension,
                                String className,
                                String detailId,
                                TickDetailKind detailKind,
                                int entityId,
                                boolean positioned,
                                int x,
                                int y,
                                int z,
                                boolean chunkPosition,
                                String eventLoop,
                                Metric metric) {
        public TickableEntry {
            detailId = detailId == null ? "" : detailId;
            eventLoop = eventLoop == null ? "" : eventLoop;
        }
    }

    public record OwnerEntry(AttributionKind kind,
                             String id,
                             String name,
                             boolean online,
                             long lastLoginEpochMillis,
                             Position position,
                             int chunkCount,
                             Metric metric,
                             List<OwnerSourceEntry> sources) {
        public OwnerEntry {
            sources = List.copyOf(sources);
        }
    }

    public record OwnerSourceEntry(String id,
                                   String name,
                                   Position position,
                                   int chunkCount,
                                   Metric metric) {
    }

    public enum AttributionKind {
        PLAYER,
        LOADER,
        SYSTEM,
        UNKNOWN
    }
}
