package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import com.moepus.serverwarashi.client.heatmap.ChunkHeatmapModel;
import com.moepus.serverwarashi.client.heatmap.ChunkHeatmapRenderer;
import com.moepus.serverwarashi.client.heatmap.HeatmapOffscreenIndicators;
import com.moepus.serverwarashi.client.screen.PerfDisplayText;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MapOverlayRenderer {
    private static final int MAX_TOOLTIP_OBJECTS = 3;
    private static final ChunkHeatmapRenderer RENDERER = new ChunkHeatmapRenderer();

    private MapOverlayRenderer() {
    }

    public static List<Component> render(GuiGraphics graphics, MapRenderContext context) {
        MapRenderContext.View view = context.view();
        float scale = view.transform().scale();
        if (!(scale > 0.0F) || !Float.isFinite(scale)) {
            return List.of();
        }
        MapRenderContext.ScreenArea area = view.screenArea();
        graphics.enableScissor(area.origin().x(), area.origin().y(),
                area.origin().x() + area.size().width(),
                area.origin().y() + area.size().height());
        try {
            return renderClipped(graphics, context);
        } finally {
            graphics.disableScissor();
        }
    }

    private static List<Component> renderClipped(GuiGraphics graphics,
                                                 MapRenderContext context) {
        MapRenderContext.View view = context.view();
        float scale = view.transform().scale();
        MapRenderContext.WorldPoint mouse = view.pointer().world();
        var hover = RENDERER.render(new ChunkHeatmapRenderer.RenderContext(
                context.dimension().toString(),
                new ChunkHeatmapRenderer.ViewTransform(
                        view.viewport(), view.transform().pose(), scale),
                new ChunkHeatmapRenderer.Hover(mouse.x(), mouse.z(),
                        Mth.floor(mouse.x()) >> 4, Mth.floor(mouse.z()) >> 4)));
        MapRenderContext.WorldPoint center = view.transform().center();
        HeatmapOffscreenIndicators.render(graphics, new HeatmapOffscreenIndicators.RenderContext(
                context.dimension().toString(), view.visible(), center));
        return view.screenArea().contains(view.pointer().screen())
                ? tooltip(hover) : List.of();
    }

    public static void renderTooltip(GuiGraphics graphics,
                                     List<Component> lines,
                                     MapRenderContext.ScreenPoint mouse) {
        if (!lines.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(),
                    mouse.x(), mouse.y());
        }
    }

    public static void close() {
        RENDERER.close();
        HeatmapOffscreenIndicators.close();
    }

    private static List<Component> tooltip(ChunkHeatmapRenderer.HoverResult hover) {
        if (hover == null) {
            return List.of();
        }
        return hover.loader() != null ? loaderTooltip(hover.loader()) : chunkTooltip(hover.chunk());
    }

    private static List<Component> chunkTooltip(ChunkHeatmapModel.HoverInfo hover) {
        if (hover == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        if (hover.metric() != null) {
            lines.add(Component.translatable(
                    "screen.serverwarashi.heatmap.mspt", format(hover.metric().mspt())));
            lines.add(Component.translatable(
                    "screen.serverwarashi.heatmap.max", format(hover.metric().max())));
        }
        if (hover.possibleWeak()) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.possible_weak"));
        }
        appendWeakEntities(lines, hover);
        appendTopObjects(lines, hover.topEntities(), TickType.ENTITY);
        appendTopObjects(lines, hover.topBlockEntities(), TickType.BLOCK_ENTITY);
        appendLoaderSources(lines, hover);
        return List.copyOf(lines);
    }

    private static void appendWeakEntities(List<Component> lines,
                                           ChunkHeatmapModel.HoverInfo hover) {
        if (hover.weakEntityCount() == 0) {
            return;
        }
        lines.add(Component.translatable("screen.serverwarashi.heatmap.weak_entities",
                hover.weakEntityCount()));
        lines.add(Component.translatable("screen.serverwarashi.heatmap.estimated_entity_types"));
        hover.entityTypeCounts().entrySet().stream()
                .sorted(Map.Entry.<WeakEntityType, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TOOLTIP_OBJECTS)
                .forEach(entry -> lines.add(Component.translatable(
                        "screen.serverwarashi.heatmap.object_count",
                        PerfDisplayText.sampledEntityName(entry.getKey()), entry.getValue())));
    }

    private static void appendTopObjects(List<Component> lines,
                                         List<PerfReportData.ObjectCount> objects,
                                         TickType type) {
        if (objects.isEmpty()) {
            return;
        }
        String heading = type == TickType.ENTITY
                ? "screen.serverwarashi.heatmap.top_entities"
                : "screen.serverwarashi.heatmap.top_block_entities";
        lines.add(Component.translatable(heading));
        objects.stream().limit(MAX_TOOLTIP_OBJECTS).forEach(object -> lines.add(Component.translatable(
                "screen.serverwarashi.heatmap.object_count",
                PerfDisplayText.objectName(
                        object.detailKind(), object.className(), object.detailId()), object.count())));
    }

    private static void appendLoaderSources(List<Component> lines,
                                            ChunkHeatmapModel.HoverInfo hover) {
        for (ChunkHeatmapModel.LoaderSource source : hover.loaderSources()) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loaded_by",
                    PerfDisplayText.resource(source.name())));
            if (source.controllable()) {
                lines.add(loaderStatus(source.working()));
            }
        }
        if (hover.omittedLoaderSources() > 0) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loaded_by_more",
                    hover.omittedLoaderSources()));
        }
        if (hover.incomplete()) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loader_data_incomplete"));
        }
    }

    private static List<Component> loaderTooltip(ChunkHeatmapModel.LoaderIcon loader) {
        Component location = loader.derivedFromRegion()
                ? Component.translatable("screen.serverwarashi.heatmap.loader_chunk",
                Math.floorDiv(loader.x(), 16), Math.floorDiv(loader.z(), 16))
                : Component.translatable("screen.serverwarashi.heatmap.loader_position",
                loader.x(), loader.y(), loader.z());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.serverwarashi.heatmap.loader_name",
                PerfDisplayText.resource(loader.name())));
        lines.add(location);
        if (loader.controllable()) {
            lines.add(loaderStatus(loader.working()));
        }
        return List.copyOf(lines);
    }

    private static Component loaderStatus(boolean working) {
        return Component.translatable(working
                ? "screen.serverwarashi.heatmap.loader_working"
                : "screen.serverwarashi.heatmap.loader_inactive");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
