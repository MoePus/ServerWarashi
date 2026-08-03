package com.moepus.serverwarashi.client.compat.xaero;

import com.mojang.blaze3d.platform.Window;
import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapOverlayControls;
import com.moepus.serverwarashi.api.client.map.MapRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import xaero.map.gui.GuiMap;

import java.util.Objects;

public final class XaeroHeatmapOverlay {
    private XaeroHeatmapOverlay() {
    }

    public static void render(GuiGraphics graphics,
                              GuiMap screen,
                              RenderInput input) {
        XaeroMapAccess.View map = XaeroMapAccess.view(screen);
        float scale = (float) guiScale(map.camera().scale());
        if (!(scale > 0.0F)) {
            return;
        }
        if (map.dimension() == null) {
            return;
        }
        var center = new MapRenderContext.WorldPoint(
                map.camera().x(), map.camera().z());
        var centered = new MapRenderContext.CenteredView(center, scale, input.pointer());
        MapRenderContext context = context(graphics, screen,
                new ContextInput(map.dimension(), centered));
        var lines = MapOverlayApi.render(XaeroMapIntegration.ID, graphics, context);
        if (input.controls() == null || !input.controls().isMouseOver(
                input.pointer().x(), input.pointer().y())) {
            MapOverlayApi.renderTooltip(graphics, lines, context.view().pointer().screen());
        }
    }

    private static MapRenderContext context(GuiGraphics graphics,
                                            GuiMap screen,
                                            ContextInput input) {
        var screenSize = new MapRenderContext.ScreenSize(screen.width, screen.height);
        var view = MapRenderContext.centered(
                screenSize, graphics.pose().last().pose(), input.centered());
        return new MapRenderContext(input.dimension(), view);
    }

    private static double guiScale(double mapScale) {
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
        return mapScale / guiScale / interfaceScale;
    }

    public record RenderInput(MapRenderContext.ScreenPoint pointer,
                              MapOverlayControls controls) {
        public RenderInput {
            Objects.requireNonNull(pointer, "pointer");
        }
    }

    private record ContextInput(ResourceLocation dimension,
                                MapRenderContext.CenteredView centered) {
    }
}
