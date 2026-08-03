package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapOverlayApi;
import com.moepus.serverwarashi.api.client.map.MapOverlayIntegration;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapOverlayRegistryTest {
    private static final ResourceLocation FIRST_ID = ResourceLocation.parse("test:first");
    private static final ResourceLocation SECOND_ID = ResourceLocation.parse("test:second");
    private static final Screen SCREEN = new Screen(Component.empty()) {
    };

    @AfterEach
    void resetRegistry() {
        MapOverlayRegistry.resetForTests();
    }

    @Test
    void registersIntegrationsAndRejectsDuplicateIds() {
        MapOverlayRegistry.register(integration(FIRST_ID, false));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> MapOverlayRegistry.register(integration(FIRST_ID, true)));

        assertTrue(exception.getMessage().contains(FIRST_ID.toString()));
        assertEquals(1, MapOverlayRegistry.registeredCountForTests());
        assertTrue(MapOverlayRegistry.isEnabled(FIRST_ID));
        assertFalse(MapOverlayRegistry.isEnabled(SECOND_ID));
    }

    @Test
    void detectsAnOpenMapAcrossEnabledIntegrations() {
        AtomicInteger closedProbeCount = new AtomicInteger();
        MapOverlayRegistry.register(integration(FIRST_ID, screen -> {
            closedProbeCount.incrementAndGet();
            return false;
        }));
        MapOverlayRegistry.register(integration(SECOND_ID, true));

        assertTrue(MapOverlayRegistry.isAnyMapOpen(SCREEN));
        assertEquals(1, closedProbeCount.get());
    }

    @Test
    void disablesOnlyTheIntegrationWhoseProbeFails() {
        AtomicInteger brokenProbeCount = new AtomicInteger();
        MapOverlayRegistry.register(integration(FIRST_ID, screen -> {
            brokenProbeCount.incrementAndGet();
            throw new LinkageError("missing optional map API");
        }));
        MapOverlayRegistry.register(integration(SECOND_ID, true));

        assertTrue(MapOverlayRegistry.isAnyMapOpen(SCREEN));
        assertFalse(MapOverlayRegistry.isEnabled(FIRST_ID));
        assertTrue(MapOverlayRegistry.isEnabled(SECOND_ID));
        assertTrue(MapOverlayRegistry.isAnyMapOpen(SCREEN));
        assertEquals(1, brokenProbeCount.get());
    }

    @Test
    void explicitDisableExcludesAnIntegrationFromProbes() {
        AtomicInteger probeCount = new AtomicInteger();
        MapOverlayRegistry.register(integration(FIRST_ID, screen -> {
            probeCount.incrementAndGet();
            return true;
        }));

        MapOverlayRegistry.disable(FIRST_ID, "test phase", new IllegalStateException("failed"));

        assertFalse(MapOverlayRegistry.isAnyMapOpen(SCREEN));
        assertFalse(MapOverlayRegistry.isEnabled(FIRST_ID));
        assertEquals(0, probeCount.get());
    }

    @Test
    void publicUiFactoriesRejectDisabledIntegrations() {
        MapOverlayRegistry.register(integration(FIRST_ID, true));
        MapOverlayRegistry.disable(FIRST_ID, "test phase", new IllegalStateException("failed"));
        AtomicInteger addedButtons = new AtomicInteger();

        assertTrue(MapOverlayApi.createControls(
                FIRST_ID, button -> addedButtons.incrementAndGet()).isEmpty());
        assertTrue(MapOverlayApi.contextMenu(FIRST_ID,
                new MapTarget(ResourceLocation.parse("minecraft:overworld"), 0, 0)).isEmpty());
        assertEquals(0, addedButtons.get());
    }

    @Test
    void nullScreenSkipsProbesWithoutDisablingIntegrations() {
        AtomicInteger probeCount = new AtomicInteger();
        MapOverlayRegistry.register(integration(FIRST_ID, screen -> {
            probeCount.incrementAndGet();
            throw new NullPointerException("screen");
        }));

        assertFalse(MapOverlayRegistry.isAnyMapOpen(null));
        assertTrue(MapOverlayRegistry.isEnabled(FIRST_ID));
        assertEquals(0, probeCount.get());
    }

    private static MapOverlayIntegration integration(ResourceLocation id, boolean open) {
        return integration(id, screen -> open);
    }

    private static MapOverlayIntegration integration(ResourceLocation id, ScreenProbe probe) {
        return new MapOverlayIntegration() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public boolean isMapOpen(Screen screen) {
                return probe.isMapOpen(screen);
            }
        };
    }

    @FunctionalInterface
    private interface ScreenProbe {
        boolean isMapOpen(Screen screen);
    }
}
