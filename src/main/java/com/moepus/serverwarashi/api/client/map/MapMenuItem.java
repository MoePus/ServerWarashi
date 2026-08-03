package com.moepus.serverwarashi.api.client.map;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * A map-neutral context menu command.
 */
public record MapMenuItem(Component label, boolean active, Runnable action) {
    public MapMenuItem {
        label = Objects.requireNonNull(label, "label").copy();
        action = Objects.requireNonNull(action, "action");
    }

    @Override
    public Component label() {
        return label.copy();
    }
}
