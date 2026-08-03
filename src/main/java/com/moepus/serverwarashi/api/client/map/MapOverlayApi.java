package com.moepus.serverwarashi.api.client.map;

import com.moepus.serverwarashi.client.map.MapContextMenuService;
import com.moepus.serverwarashi.client.map.MapOverlayRegistry;
import com.moepus.serverwarashi.client.map.MapOverlayRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client-side entry point used by world-map compatibility addons.
 */
public final class MapOverlayApi {
    private MapOverlayApi() {
    }

    /** Register an integration during client setup. Integrations cannot be replaced at runtime. */
    public static void register(MapOverlayIntegration integration) {
        MapOverlayRegistry.register(integration);
    }

    /** Returns whether any enabled integration owns the current screen. */
    public static boolean isAnyMapOpen(Screen screen) {
        return MapOverlayRegistry.isAnyMapOpen(screen);
    }

    /** Returns whether a registered integration is still enabled. */
    public static boolean isEnabled(ResourceLocation integrationId) {
        return MapOverlayRegistry.isEnabled(integrationId);
    }

    /**
     * Create the standard profiling controls and deliver their buttons to the map screen.
     * Returns empty when the owning integration has been disabled.
     */
    public static Optional<MapOverlayControls> createControls(
            ResourceLocation integrationId,
            Consumer<Button> buttonSink) {
        Objects.requireNonNull(integrationId, "integrationId");
        Objects.requireNonNull(buttonSink, "buttonSink");
        return isEnabled(integrationId)
                ? Optional.of(MapOverlayControls.create(buttonSink))
                : Optional.empty();
    }

    /**
     * Render the world overlay and return tooltip lines for the current pointer position.
     * The caller chooses when to draw the tooltip so native map UI can remain above it.
     * Call this with the GuiGraphics pose in screen space; the world transform is supplied
     * separately by {@link MapRenderContext.View#transform()}.
     */
    public static List<Component> render(ResourceLocation integrationId,
                                         GuiGraphics graphics,
                                         MapRenderContext context) {
        Objects.requireNonNull(integrationId, "integrationId");
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(context, "context");
        if (!MapOverlayRegistry.isEnabled(integrationId)) {
            return List.of();
        }
        try {
            graphics.flush();
            return MapOverlayRenderer.render(graphics, context);
        } catch (Throwable throwable) {
            disable(integrationId, "render", throwable);
            return List.of();
        }
    }

    public static void renderTooltip(GuiGraphics graphics,
                                     List<Component> lines,
                                     MapRenderContext.ScreenPoint mouse) {
        MapOverlayRenderer.renderTooltip(graphics, List.copyOf(lines), mouse);
    }

    /** Build map-neutral commands for a selected chunk, or an empty list when disabled. */
    public static List<MapMenuItem> contextMenu(ResourceLocation integrationId,
                                                MapTarget target) {
        Objects.requireNonNull(integrationId, "integrationId");
        Objects.requireNonNull(target, "target");
        return isEnabled(integrationId) ? MapContextMenuService.items(target) : List.of();
    }

    /** Disable one broken optional integration without affecting other map integrations. */
    public static void disable(ResourceLocation integrationId,
                               String phase,
                               Throwable throwable) {
        boolean wasEnabled = MapOverlayRegistry.isEnabled(integrationId);
        MapOverlayRegistry.disable(integrationId, phase, throwable);
        if (wasEnabled) {
            MapOverlayRenderer.close();
        }
    }
}
