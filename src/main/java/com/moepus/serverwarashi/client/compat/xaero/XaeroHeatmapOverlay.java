package com.moepus.serverwarashi.client.compat.xaero;

import com.mojang.blaze3d.platform.Window;
import com.moepus.serverwarashi.client.heatmap.ChunkHeatmapModel;
import com.moepus.serverwarashi.client.heatmap.ChunkHeatmapRenderer;
import com.moepus.serverwarashi.client.heatmap.HeatmapViewport;
import com.moepus.serverwarashi.client.screen.PerfDisplayText;
import com.moepus.serverwarashi.common.mixin.compat.xaero.XaeroGuiMapAccessor;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import xaero.map.gui.GuiMap;
import xaero.map.gui.ScreenBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class XaeroHeatmapOverlay {
    private static final ChunkHeatmapRenderer RENDERER = new ChunkHeatmapRenderer();
    private static boolean disabled;

    private XaeroHeatmapOverlay() {
    }

    public static boolean isMapOpen(Screen screen) {
        return !disabled && (screen instanceof GuiMap
                || screen instanceof ScreenBase base && base.parent instanceof GuiMap);
    }

    public static void render(GuiGraphics graphics, GuiMap screen, int mouseX, int mouseY) {
        if (disabled) {
            return;
        }
        XaeroGuiMapAccessor map = (XaeroGuiMapAccessor) screen;
        double scale = guiScale(map.serverwarashi$getScale());
        if (!(scale > 0.0D)) {
            return;
        }
        double cameraX = map.serverwarashi$getCameraX();
        double cameraZ = map.serverwarashi$getCameraZ();
        var mapWorld = map.serverwarashi$getMapProcessor().getMapWorld();
        if (mapWorld == null || mapWorld.getCurrentDimensionId() == null) {
            return;
        }
        String dimension = mapWorld.getCurrentDimensionId().location().toString();
        View view = view(screen, mouseX, mouseY, cameraX, cameraZ, scale);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(screen.width / 2.0D, screen.height / 2.0D, 0.0D);
        pose.scale((float) scale, (float) scale, 1.0F);
        pose.translate(-cameraX, -cameraZ, 0.0D);
        graphics.flush();
        var hover = RENDERER.render(new ChunkHeatmapRenderer.RenderContext(
                dimension,
                new ChunkHeatmapRenderer.ViewTransform(view.viewport(), pose.last().pose(), (float) scale),
                new ChunkHeatmapRenderer.Hover(view.worldX(), view.worldZ(),
                        view.hoverChunkX(), view.hoverChunkZ())));
        pose.popPose();

        if (hover != null && !XaeroPerfControls.isMouseOver(mouseX, mouseY)) {
            if (hover.loader() != null) {
                renderLoaderTooltip(graphics, mouseX, mouseY, hover.loader());
            } else if (hover.chunk() != null) {
                renderTooltip(graphics, mouseX, mouseY, hover.chunk());
            }
        }
    }

    private static View view(Screen screen, int mouseX, int mouseY,
                             double cameraX, double cameraZ, double scale) {
        int width = Math.max(1, Mth.ceil(screen.width / scale));
        int height = Math.max(1, Mth.ceil(screen.height / scale));
        int minX = Mth.floor(cameraX - width / 2.0D);
        int minZ = Mth.floor(cameraZ - height / 2.0D);
        double worldX = (mouseX - screen.width / 2.0D) / scale + cameraX;
        double worldZ = (mouseY - screen.height / 2.0D) / scale + cameraZ;
        return new View(new HeatmapViewport(minX, minZ, width, height),
                worldX, worldZ, Mth.floor(worldX) >> 4, Mth.floor(worldZ) >> 4);
    }

    private static double guiScale(double mapScale) {
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
        return mapScale / guiScale / interfaceScale;
    }

    private static void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                      ChunkHeatmapModel.HoverInfo hover) {
        List<Component> lines = new ArrayList<>();
        if (hover.metric() != null) {
            lines.add(Component.translatable(
                    "screen.serverwarashi.heatmap.mspt", format(hover.metric().mspt())));
            lines.add(Component.translatable(
                    "screen.serverwarashi.heatmap.p98", format(hover.metric().p98())));
        }
        appendTopObjects(lines, hover.topEntities(), TickType.ENTITY);
        appendTopObjects(lines, hover.topBlockEntities(), TickType.BLOCK_ENTITY);
        for (ChunkHeatmapModel.LoaderSource source : hover.loaderSources()) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loaded_by",
                    PerfDisplayText.resource(source.name())));
        }
        if (hover.omittedLoaderSources() > 0) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loaded_by_more",
                    hover.omittedLoaderSources()));
        }
        if (hover.incomplete()) {
            lines.add(Component.translatable("screen.serverwarashi.heatmap.loader_data_incomplete"));
        }
        graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
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
        objects.stream().limit(3).forEach(object -> lines.add(Component.translatable(
                "screen.serverwarashi.heatmap.object_count",
                PerfDisplayText.objectName(
                        object.detailKind(), object.className(), object.detailId()), object.count())));
    }

    private static void renderLoaderTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                            ChunkHeatmapModel.LoaderIcon loader) {
        List<Component> lines = List.of(
                Component.translatable("screen.serverwarashi.heatmap.loader_name",
                        PerfDisplayText.resource(loader.name())),
                Component.translatable("screen.serverwarashi.heatmap.loader_position",
                        loader.x(), loader.y(), loader.z()));
        graphics.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public static void close() {
        RENDERER.close();
    }

    public static void disable() {
        disabled = true;
        close();
    }

    private record View(HeatmapViewport viewport, double worldX, double worldZ,
                        int hoverChunkX, int hoverChunkZ) {
    }
}
