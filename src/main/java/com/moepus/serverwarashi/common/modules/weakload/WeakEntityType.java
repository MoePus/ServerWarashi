package com.moepus.serverwarashi.common.modules.weakload;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record WeakEntityType(Kind kind, String id) implements Comparable<WeakEntityType> {
    public WeakEntityType {
        Objects.requireNonNull(kind, "kind");
        if (id == null || ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("Invalid weak entity type ID: " + id);
        }
    }

    @Override
    public int compareTo(WeakEntityType other) {
        int idComparison = id.compareTo(other.id);
        return idComparison != 0 ? idComparison : kind.compareTo(other.kind);
    }

    public enum Kind {
        ITEM,
        ENTITY
    }
}
