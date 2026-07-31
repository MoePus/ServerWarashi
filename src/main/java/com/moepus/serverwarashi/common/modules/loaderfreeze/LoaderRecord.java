package com.moepus.serverwarashi.common.modules.loaderfreeze;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent user intent and first known placer for one block loader. */
public record LoaderRecord(boolean manuallyFrozen, Optional<UUID> placedBy) {
    static final LoaderRecord EMPTY = new LoaderRecord(false, Optional.empty());

    public LoaderRecord {
        placedBy = Objects.requireNonNull(placedBy, "placedBy");
    }

    LoaderRecord withManuallyFrozen(boolean frozen) {
        return manuallyFrozen == frozen ? this : new LoaderRecord(frozen, placedBy);
    }

    LoaderRecord withPlacedByIfAbsent(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return placedBy.isPresent() ? this : new LoaderRecord(manuallyFrozen, Optional.of(playerId));
    }

    boolean shouldPersist() {
        return manuallyFrozen || placedBy.isPresent();
    }
}
