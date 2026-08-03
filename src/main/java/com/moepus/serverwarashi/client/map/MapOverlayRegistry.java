package com.moepus.serverwarashi.client.map;

import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.api.client.map.MapOverlayIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Internal registry and failure boundary for map overlay integrations.
 */
public final class MapOverlayRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, RegisteredIntegration> INTEGRATIONS = new LinkedHashMap<>();

    private MapOverlayRegistry() {
    }

    public static synchronized void register(MapOverlayIntegration integration) {
        Objects.requireNonNull(integration, "integration");
        ResourceLocation id = Objects.requireNonNull(integration.id(), "integration id");
        if (INTEGRATIONS.containsKey(id)) {
            throw new IllegalArgumentException("Map overlay integration already registered: " + id);
        }
        INTEGRATIONS.put(id, new RegisteredIntegration(integration));
    }

    public static boolean isAnyMapOpen(Screen screen) {
        if (screen == null) {
            return false;
        }
        for (RegistryEntry entry : enabledSnapshot()) {
            try {
                if (entry.integration().isMapOpen(screen)) {
                    return true;
                }
            } catch (Throwable throwable) {
                disable(entry.id(), "map screen probe", throwable);
            }
        }
        return false;
    }

    public static synchronized boolean isEnabled(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        RegisteredIntegration registered = INTEGRATIONS.get(id);
        return registered != null && registered.enabled;
    }

    public static synchronized void disable(ResourceLocation id, String phase, Throwable throwable) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(throwable, "throwable");
        RegisteredIntegration registered = INTEGRATIONS.get(id);
        if (registered == null || !registered.enabled) {
            return;
        }
        registered.enabled = false;
        LOGGER.error("Disabling map overlay integration {} after a {} failure", id, phase, throwable);
    }

    private static synchronized ArrayList<RegistryEntry> enabledSnapshot() {
        ArrayList<RegistryEntry> enabled = new ArrayList<>(INTEGRATIONS.size());
        INTEGRATIONS.forEach((id, registered) -> {
            if (registered.enabled) {
                enabled.add(new RegistryEntry(id, registered.integration));
            }
        });
        return enabled;
    }

    static synchronized void resetForTests() {
        INTEGRATIONS.clear();
    }

    static synchronized int registeredCountForTests() {
        return INTEGRATIONS.size();
    }

    private static final class RegisteredIntegration {
        private final MapOverlayIntegration integration;
        private boolean enabled = true;

        private RegisteredIntegration(MapOverlayIntegration integration) {
            this.integration = integration;
        }
    }

    private record RegistryEntry(ResourceLocation id, MapOverlayIntegration integration) {
    }
}
