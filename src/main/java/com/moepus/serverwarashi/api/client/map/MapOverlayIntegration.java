package com.moepus.serverwarashi.api.client.map;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side integration point for map mods that can host ServerWarashi overlays.
 */
public interface MapOverlayIntegration {
    /**
     * Returns the stable identifier used to register this integration.
     */
    ResourceLocation id();

    /**
     * Returns whether the supplied non-null screen belongs to this map integration.
     * The registry filters the normal in-game {@code null} screen before calling integrations.
     */
    boolean isMapOpen(Screen screen);
}
