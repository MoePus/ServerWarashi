package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapApiValueTypesTest {
    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(NullPointerException.class, () -> new MapTarget(null, 0, 0));
        assertThrows(NullPointerException.class, () -> new MapMenuItem(null, true, () -> {
        }));
        assertThrows(NullPointerException.class,
                () -> new MapMenuItem(Component.empty(), true, null));
    }

    @Test
    void menuItemCopiesLabelsOnInputAndAccess() {
        MutableComponent source = Component.literal("original");
        MapMenuItem item = new MapMenuItem(source, true, () -> {
        });
        source.append(" changed");

        MutableComponent exposed = item.label().copy().append(" exposed");

        assertEquals("original exposed", exposed.getString());
        assertEquals("original", item.label().getString());
    }
}
