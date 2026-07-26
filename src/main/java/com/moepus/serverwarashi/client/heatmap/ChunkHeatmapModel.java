package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkHeatmapModel {
    static final double MSPT_THRESHOLD = 0.5D;
    static final double P98_THRESHOLD = 3.0D;
    private static final double MSPT_WEIGHT = 0.65D;
    private static final double P98_WEIGHT = 0.35D;
    private static final int MAX_LOADER_QUADS = 16_384;
    private static final int MAX_LOADER_ASSOCIATIONS = 65_536;
    private static final int MAX_SOURCES_PER_CHUNK = 4;
    private static final ColorStop LOADER_GREEN = new ColorStop(36, 64, 220, 120);
    private static final ColorStop LOADER_CYAN = new ColorStop(48, 45, 220, 235);
    private static final ColorStop[] COLORS = {
            new ColorStop(96, 255, 214, 64),
            new ColorStop(112, 255, 145, 32),
            new ColorStop(136, 244, 67, 54),
            new ColorStop(160, 190, 45, 220)
    };

    private ChunkHeatmapModel() {
    }

    public static Plan build(PerfReportData report, String dimension, HeatmapViewport coverage) {
        if (report == null) {
            return Plan.EMPTY;
        }
        Map<Long, Cell> cells = new HashMap<>();
        BuildBudget budget = new BuildBudget();
        addLoaderSources(report, dimension,
                new RegionBuildContext(ViewBounds.from(coverage), cells, budget));
        addSampledChunks(report, dimension, coverage, cells);
        List<LoaderIcon> icons = loaderIcons(report, dimension, coverage);
        boolean incomplete = budget.exhausted || report.omittedLoaderSources() > 0
                || report.loaderSources().stream().anyMatch(source -> source.omittedRegions() > 0);
        return toPlan(cells, icons, incomplete);
    }

    private static List<LoaderIcon> loaderIcons(PerfReportData report,
                                                String dimension,
                                                HeatmapViewport coverage) {
        List<LoaderIcon> result = new ArrayList<>();
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            PerfReportData.LoaderPosition position = source.position();
            if (position != null && dimension.equals(source.dimension())
                    && coverage.containsPoint(position.x(), position.z())) {
                result.add(new LoaderIcon(source.sourceId(), source.loaderName(),
                        position.x(), position.y(), position.z()));
            }
        }
        return List.copyOf(result);
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
        ViewBounds view = context.view;
        long regionMaxX = (long) region.minChunkX() + region.width() - 1L;
        long regionMaxZ = (long) region.minChunkZ() + region.height() - 1L;
        int minX = Math.max(region.minChunkX(), view.minX);
        int minZ = Math.max(region.minChunkZ(), view.minZ);
        int maxX = (int) Math.min(regionMaxX, view.maxX);
        int maxZ = (int) Math.min(regionMaxZ, view.maxZ);
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
        cells.forEach((key, cell) -> {
            quads.add(new Quad(cell.chunkX, cell.chunkZ, cell.argb));
            hover.put(key, new HoverInfo(cell.metric, cell.topEntities, cell.topBlockEntities,
                    List.copyOf(cell.loaderSources.values()), cell.omittedLoaderSources, incomplete));
        });
        return new Plan(quads, hover, icons);
    }

    static boolean shouldRender(PerfReportData.Metric metric) {
        return metric.mspt() >= MSPT_THRESHOLD || metric.p98() >= P98_THRESHOLD;
    }

    static double severity(PerfReportData.Metric metric) {
        double normalizedMspt = metric.mspt() / MSPT_THRESHOLD;
        double normalizedP98 = metric.p98() / P98_THRESHOLD;
        return normalizedMspt * MSPT_WEIGHT + normalizedP98 * P98_WEIGHT;
    }

    static int color(PerfReportData.Metric metric) {
        double severity = Math.max(1.0D, severity(metric));
        double position = Math.log(severity) / Math.log(2.0D);
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

    public record LoaderIcon(String id, String name, int x, int y, int z) {
    }

    public record HoverInfo(PerfReportData.Metric metric,
                            List<PerfReportData.ObjectCount> topEntities,
                            List<PerfReportData.ObjectCount> topBlockEntities,
                            List<LoaderSource> loaderSources,
                            int omittedLoaderSources,
                            boolean incomplete) {
        public HoverInfo {
            topEntities = List.copyOf(topEntities);
            topBlockEntities = List.copyOf(topBlockEntities);
            loaderSources = List.copyOf(loaderSources);
        }
    }

    public record Plan(List<Quad> quads, Map<Long, HoverInfo> hover, List<LoaderIcon> icons) {
        static final Plan EMPTY = new Plan(List.of(), Map.of(), List.of());

        public Plan {
            quads = List.copyOf(quads);
            hover = Map.copyOf(hover);
            icons = List.copyOf(icons);
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
    }

    private static final class Cell {
        private final int chunkX;
        private final int chunkZ;
        private final Map<String, LoaderSource> loaderSources = new LinkedHashMap<>();
        private int argb;
        private PerfReportData.Metric metric;
        private List<PerfReportData.ObjectCount> topEntities = List.of();
        private List<PerfReportData.ObjectCount> topBlockEntities = List.of();
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
    }

    private record ViewBounds(int minX, int minZ, int maxX, int maxZ) {
        static ViewBounds from(HeatmapViewport viewport) {
            if (viewport.width() == 0 || viewport.height() == 0) {
                return new ViewBounds(0, 0, -1, -1);
            }
            return new ViewBounds(Math.floorDiv(viewport.minX(), 16), Math.floorDiv(viewport.minZ(), 16),
                    Math.floorDiv(viewport.maxX() - 1, 16), Math.floorDiv(viewport.maxZ() - 1, 16));
        }

        boolean valid() {
            return minX <= maxX && minZ <= maxZ;
        }
    }

    private record RegionBuildContext(ViewBounds view, Map<Long, Cell> cells, BuildBudget budget) {
        boolean add(int chunkX, int chunkZ, LoaderSource loader) {
            if (!budget.claimAssociation()) {
                return false;
            }
            long key = ChunkPos.asLong(chunkX, chunkZ);
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

    private record ColorStop(int alpha, int red, int green, int blue) {
        int interpolate(ColorStop other, double amount) {
            double clamped = Math.max(0.0D, Math.min(1.0D, amount));
            return pack(lerp(alpha, other.alpha, clamped), lerp(red, other.red, clamped),
                    lerp(green, other.green, clamped), lerp(blue, other.blue, clamped));
        }

        private static int lerp(int from, int to, double amount) {
            return (int) Math.round(from + (to - from) * amount);
        }

        private static int pack(int alpha, int red, int green, int blue) {
            return alpha << 24 | red << 16 | green << 8 | blue;
        }
    }
}
