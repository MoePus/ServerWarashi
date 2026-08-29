package com.moepus.serverwarashi.api.client.map;

import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapOverlayControlsTest {
    @Test
    void showsControlsForConnectedSingleplayerWithoutOperatorCommands() {
        assertTrue(MapOverlayControls.hasPerfPermission(true, false, true));
        assertTrue(MapOverlayControls.hasPerfPermission(true, true, false));
        assertFalse(MapOverlayControls.hasPerfPermission(true, false, false));
        assertFalse(MapOverlayControls.hasPerfPermission(false, false, true));
    }

    @Test
    void initializesBeforeDeliveringButtons() {
        List<Button> delivered = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> MapOverlayControls.create(
                delivered::add, controls -> {
                    throw new IllegalStateException("initialization failed");
                }));

        assertTrue(delivered.isEmpty());
    }

    @Test
    void hidesDeliveredButtonsWhenInstallationFails() {
        List<Button> delivered = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> MapOverlayControls.create(button -> {
            delivered.add(button);
            if (delivered.size() == 2) {
                throw new IllegalStateException("second delivery failed");
            }
        }, controls -> {
        }));

        assertEquals(2, delivered.size());
        delivered.forEach(button -> {
            assertFalse(button.visible);
            assertFalse(button.active);
        });
    }

    @Test
    void deliversInitializedButtonsOnSuccess() {
        List<Button> delivered = new ArrayList<>();

        MapOverlayControls.create(delivered::add, controls -> {
        });

        assertEquals(2, delivered.size());
        delivered.forEach(button -> {
            assertTrue(button.visible);
            assertTrue(button.active);
        });
    }
}
