package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChunkHeatmapModel {
    static final double MSPT_THRESHOLD = 0.5D;
    static final double MAX_THRESHOLD = 3.0D;
    private static final double MSPT_WEIGHT = 0.65D;
    private static final double MAX_WEIGHT = 0.35D;
    private static final int MAX_LOADER_QUADS = 16_384;
    private static final int MAX_LOADER_ASSOCIATIONS = 65_536;
    private static final int MAX_SOURCES_PER_CHUNK = 4;
    private static final HeatmapColorStop LOADER_GREEN = new HeatmapColorStop(36, 64, 220, 120);
    private static final HeatmapColorStop LOADER_CYAN = new HeatmapColorStop(48, 45, 220, 235);
    private static final HeatmapColorStop[] COLORS = {
            new HeatmapColorStop(96, 255, 214, 64),
            new HeatmapColorStop(112, 255, 145, 32),
            new HeatmapColorStop(136, 244, 67, 54),
            new HeatmapColorStop(160, 190, 45, 220)
    };
    static final double PURPLE_CONTRIBUTION = 1 << (COLORS.length - 1);

    private ChunkHeatmapModel() {
    }

    public static Plan build(PerfReportData report, String dimension, HeatmapViewport coverage) {
        if (report == null) {
            return Plan.EMPTY;
        }
        Map<Long, Cell> cells = new HashMap<>();
        Set<Long> loaderChunks = new HashSet<>();
        BuildBudget budget = new BuildBudget();
        addLoaderSources(report, dimension,
                new RegionBuildContext(HeatmapViewBounds.from(coverage), cells, budget, loaderChunks));
        addSampledChunks(report, dimension, coverage, cells);
        boolean incomplete = budget.exhausted || report.omittedLoaderSources() > 0
                || report.loaderSources().stream().anyMatch(source -> source.omittedRegions() > 0);
        addWeakEdges(cells, loaderChunks, incomplete);
        return toPlan(cells, loaderIcons(report, dimension, coverage), incomplete);
    }

    static Plan applyWeakChunks(Plan base,
                                WeakChunkSnapshot weakSnapshot,
                                String dimension,
                                HeatmapViewport coverage) {
        if (weakSnapshot == null || !dimension.equals(weakSnapshot.dimension())) {
            return base;
        }
        return WeakHeatmapPlan.apply(base, dimension,
                visibleWeakChunks(weakSnapshot.chunks(), coverage), dimension);
    }

    static Plan applyWeakChunks(Plan base,
                                List<WeakChunkSnapshot.ChunkEntry> weakChunks,
                                String weakDimension,
                                String dimension) {
        return WeakHeatmapPlan.apply(base, weakDimension, weakChunks, dimension);
    }

    private static void addWeakEdges(Map<Long, Cell> cells,
                                     Set<Long> loaderChunks,
                                     boolean incomplete) {
        if (!incomplete) {
            WeakEdgeMask.find(loaderChunks).forEach((chunk, mask) -> {
                Cell cell = cells.get(chunk);
                if (cell != null) {
                    cell.weakEdgeMask = mask;
                }
            });
        }
    }

    private static List<WeakChunkSnapshot.ChunkEntry> visibleWeakChunks(
            List<WeakChunkSnapshot.ChunkEntry> chunks,
            HeatmapViewport coverage) {
        return chunks.stream()
                .filter(entry -> coverage.intersectsChunk(entry.chunkX(), entry.chunkZ()))
                .toList();
    }

    private static List<LoaderIcon> loaderIcons(PerfReportData report,
                                                String dimension,
                                                HeatmapViewport coverage) {
        List<LoaderIcon> result = new ArrayList<>();
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            if (!dimension.equals(source.dimension())) {
                continue;
            }
            PerfReportData.LoaderPosition position = source.position();
            if (position != null && coverage.containsPoint(position.x(), position.z())) {
                result.add(new LoaderIcon(source.sourceId(), source.loaderName(),
                        position.x(), position.y(), position.z()));
                continue;
            }
            if (position == null) {
                addRegionIcons(result, source, coverage);
            }
        }
        return List.copyOf(result);
    }

    private static void addRegionIcons(List<LoaderIcon> result,
                                       PerfReportData.LoaderSourceEntry source,
                                       HeatmapViewport coverage) {
        for (LoaderRegionIcons.Anchor anchor : LoaderRegionIcons.anchors(source.regions())) {
            int x = (anchor.chunkX() << 4) + 8;
            int z = (anchor.chunkZ() << 4) + 8;
            if (coverage.containsPoint(x, z)) {
                result.add(new LoaderIcon(source.sourceId(), source.loaderName(), x, null, z));
            }
        }
    }

    private static void addLoaderSources(PerfReportData report,
                                         String dimension,
                                         RegionBuildContext context) {
        if (!context.view.valid()) {
            return;
        }
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            if (context.budget.exhausted) {
                return;
            }
            if (!dimension.equals(source.dimension())) {
                continue;
            }
            LoaderSource loader = new LoaderSource(source.sourceId(), source.loaderName());
            for (PerfReportData.LoaderRegion region : source.regions()) {
                addLoaderRegion(loader, region, context);
                if (context.budget.exhausted) {
                    return;
                }
            }
        }
    }

    private static void addLoaderRegion(LoaderSource loader,
                                        PerfReportData.LoaderRegion region,
                                        RegionBuildContext context) {
        HeatmapViewBounds view = context.view;
        long regionMaxX = (long) region.minChunkX() + region.width() - 1L;
        long regionMaxZ = (long) region.minChunkZ() + region.height() - 1L;
        int minX = Math.max(region.minChunkX(), view.minX());
        int minZ = Math.max(region.minChunkZ(), view.minZ());
        int maxX = (int) Math.min(regionMaxX, view.maxX());
        int maxZ = (int) Math.min(regionMaxZ, view.maxZ());
        for (long chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
            for (long chunkX = minX; chunkX <= maxX; chunkX++) {
                if (!context.add((int) chunkX, (int) chunkZ, loader)) {
                    return;
                }
            }
        }
    }

    private static void addSampledChunks(PerfReportData report,
                                         String dimension,
                                         HeatmapViewport coverage,
                                         Map<Long, Cell> cells) {
        for (PerfReportData.ChunkEntry entry : report.chunks()) {
            if (!dimension.equals(entry.dimension())
                    || !coverage.intersectsChunk(entry.chunkX(), entry.chunkZ())) {
                continue;
            }
            long key = ChunkPos.asLong(entry.chunkX(), entry.chunkZ());
            Cell cell = cells.get(key);
            if (cell == null && !shouldRender(entry.metric())) {
                continue;
            }
            if (cell == null) {
                cell = new Cell(entry.chunkX(), entry.chunkZ(), color(entry.metric()));
                cells.put(key, cell);
            }
            cell.metric = entry.metric();
            cell.topEntities = entry.topEntities();
            cell.topBlockEntities = entry.topBlockEntities();
            cell.argb = shouldRender(entry.metric()) ? color(entry.metric()) : loaderColor(entry.metric());
        }
    }

    private static Plan toPlan(Map<Long, Cell> cells, List<LoaderIcon> icons, boolean incomplete) {
        List<Quad> quads = new ArrayList<>(cells.size());
        Map<Long, HoverInfo> hover = new HashMap<>(cells.size());
        Map<Long, Integer> weakEdgeMasks = new HashMap<>();
        cells.forEach((key, cell) -> {
            quads.add(new Quad(cell.chunkX, cell.chunkZ, cell.color()));
            if (cell.weakEdgeMask != 0) {
                weakEdgeMasks.put(key, cell.weakEdgeMask);
            }
            hover.put(key, new HoverInfo(cell.metric, cell.topEntities, cell.topBlockEntities,
                    0, Map.of(), cell.weakEdgeMask != 0,
                    List.copyOf(cell.loaderSources.values()), cell.omittedLoaderSources, incomplete));
        });
        return new Plan(quads, hover, icons, weakEdgeMasks);
    }

    static boolean shouldRender(PerfReportData.Metric metric) {
        return metric.mspt() >= MSPT_THRESHOLD || metric.max() >= MAX_THRESHOLD;
    }

    static double severity(PerfReportData.Metric metric) {
        double normalizedMspt = metric.mspt() / MSPT_THRESHOLD;
        double normalizedMax = metric.max() / MAX_THRESHOLD;
        return normalizedMspt * MSPT_WEIGHT + normalizedMax * MAX_WEIGHT;
    }

    static int color(PerfReportData.Metric metric) {
        return colorAtPosition(Math.log(Math.max(1.0D, severity(metric))) / Math.log(2.0D));
    }

    static int weakColor(int entityCount) {
        return colorAtPosition(weakPosition(entityCount));
    }

    static int combinedColor(PerfReportData.Metric metric, int entityCount) {
        return colorAtPosition(Math.log(combinedContribution(metric, entityCount)) / Math.log(2.0D));
    }

    static double combinedContribution(PerfReportData.Metric metric, int weakEntityCount) {
        double performanceContribution = metric == null ? 0.0D : Math.max(0.0D, severity(metric));
        if (weakEntityCount < WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) {
            return performanceContribution;
        }
        return Math.pow(2.0D, weakPosition(weakEntityCount)) + performanceContribution;
    }

    static boolean isPurple(PerfReportData.Metric metric, int weakEntityCount) {
        return combinedContribution(metric, weakEntityCount) >= PURPLE_CONTRIBUTION;
    }

    private static double weakPosition(int entityCount) {
        return Math.max(0.0D, Math.min(3.0D,
                (entityCount - WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) / 150.0D));
    }

    private static int colorAtPosition(double position) {
        position = Math.max(0.0D, Math.min(COLORS.length - 1.0D, position));
        int lower = Math.min((int) Math.floor(position), COLORS.length - 1);
        int upper = Math.min(lower + 1, COLORS.length - 1);
        return COLORS[lower].interpolate(COLORS[upper], position - lower);
    }

    static int loaderColor(PerfReportData.Metric metric) {
        double amount = metric == null ? 0.0D : severity(metric);
        return LOADER_GREEN.interpolate(LOADER_CYAN, amount);
    }

    public record Quad(int chunkX, int chunkZ, int argb) {
    }

    public record LoaderSource(String id, String name) {
    }

    public record LoaderIcon(String id, String name, int x, Integer y, int z) {
        public boolean derivedFromRegion() {
            return y == null;
        }
    }

    public record HoverInfo(PerfReportData.Metric metric,
                            List<PerfReportData.ObjectCount> topEntities,
                            List<PerfReportData.ObjectCount> topBlockEntities,
                            int weakEntityCount,
                            Map<WeakEntityType, Integer> entityTypeCounts,
                            boolean possibleWeak,
                            List<LoaderSource> loaderSources,
                            int omittedLoaderSources,
                            boolean incomplete) {
        public HoverInfo {
            topEntities = List.copyOf(topEntities);
            topBlockEntities = List.copyOf(topBlockEntities);
            entityTypeCounts = Map.copyOf(entityTypeCounts);
            loaderSources = List.copyOf(loaderSources);
        }
    }

    public record Plan(List<Quad> quads,
                       Map<Long, HoverInfo> hover,
                       List<LoaderIcon> icons,
                       Map<Long, Integer> weakEdgeMasks) {
        static final Plan EMPTY = new Plan(List.of(), Map.of(), List.of(), Map.of());

        public Plan(List<Quad> quads, Map<Long, HoverInfo> hover, List<LoaderIcon> icons) {
            this(quads, hover, icons, Map.of());
        }

        public Plan {
            quads = List.copyOf(quads);
            hover = Map.copyOf(hover);
            icons = List.copyOf(icons);
            weakEdgeMasks = Map.copyOf(weakEdgeMasks);
        }

        public int vertexCount() {
            return quads.size() * 4;
        }

        public PerfReportData.Metric metricAt(int chunkX, int chunkZ) {
            HoverInfo info = hoverAt(chunkX, chunkZ);
            return info == null ? null : info.metric();
        }

        public HoverInfo hoverAt(int chunkX, int chunkZ) {
            return hover.get(ChunkPos.asLong(chunkX, chunkZ));
        }

        public int weakEdgeMaskAt(int chunkX, int chunkZ) {
            return weakEdgeMasks.getOrDefault(ChunkPos.asLong(chunkX, chunkZ), 0);
        }
    }

    private static final class Cell {
        private final int chunkX;
        private final int chunkZ;
        private final Map<String, LoaderSource> loaderSources = new LinkedHashMap<>();
        private int argb;
        private PerfReportData.Metric metric;
        private List<PerfReportData.ObjectCount> topEntities = List.of();
        private List<PerfReportData.ObjectCount> topBlockEntities = List.of();
        private int weakEdgeMask;
        private int omittedLoaderSources;

        private Cell(int chunkX, int chunkZ, int argb) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.argb = argb;
        }

        void addLoader(LoaderSource loader) {
            if (loaderSources.containsKey(loader.id())) {
                return;
            }
            if (loaderSources.size() < MAX_SOURCES_PER_CHUNK) {
                loaderSources.put(loader.id(), loader);
            } else {
                omittedLoaderSources++;
            }
        }

        int color() {
            return metric != null && shouldRender(metric) ? ChunkHeatmapModel.color(metric) : argb;
        }
    }

    private record RegionBuildContext(HeatmapViewBounds view,
                                      Map<Long, Cell> cells,
                                      BuildBudget budget,
                                      Set<Long> loaderChunks) {
        boolean add(int chunkX, int chunkZ, LoaderSource loader) {
            if (!budget.claimAssociation()) {
                return false;
            }
            long key = ChunkPos.asLong(chunkX, chunkZ);
            loaderChunks.add(key);
            Cell cell = cells.get(key);
            if (cell == null) {
                if (cells.size() >= MAX_LOADER_QUADS) {
                    budget.exhausted = true;
                    return false;
                }
                cell = new Cell(chunkX, chunkZ, loaderColor(null));
                cells.put(key, cell);
            }
            cell.addLoader(loader);
            return true;
        }
    }

    private static final class BuildBudget {
        private int associations;
        private boolean exhausted;

        boolean claimAssociation() {
            if (associations >= MAX_LOADER_ASSOCIATIONS) {
                exhausted = true;
                return false;
            }
            associations++;
            return true;
        }
    }

}
