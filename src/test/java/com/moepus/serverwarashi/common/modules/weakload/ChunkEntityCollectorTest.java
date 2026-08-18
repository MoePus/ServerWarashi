package com.moepus.serverwarashi.common.modules.weakload;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChunkEntityCollectorTest {
    @Test
    void restartKeepsSweepIdsMonotonic() {
        var cursor = new ChunkEntityCollector.ScanCursor();

        cursor.restart();
        cursor.restart();

        assertEquals(2L, cursor.sweepId());
    }

    @Test
    void summaryPreservesEstimatedCountsAndRepresentativeCoordinates() {
        WeakEntityType cow = new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");
        WeakEntityType item = new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:stone");
        var summary = new ChunkEntityCollector.ChunkEntitySummary(1, 2, 300, Map.of(
                cow, new ChunkEntityCollector.TypeEstimate(100, 20, 64, 36),
                item, new ChunkEntityCollector.TypeEstimate(200, 21, 65, 37)));

        assertEquals(item, summary.topTypes(1).getFirst().getKey());
        assertEquals(21, summary.topTypes(1).getFirst().getValue().x());
        assertEquals(Map.of(cow, 100, item, 200), summary.toWeakEntry().entityTypeCounts());
    }

    @Test
    void summaryBelowWarningThresholdDoesNotBecomeAWeakWarning() {
        WeakEntityType cow = new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");
        var summary = new ChunkEntityCollector.ChunkEntitySummary(1, 2, 49, Map.of(
                cow, new ChunkEntityCollector.TypeEstimate(49, 20, 64, 36)));

        assertNull(summary.toWeakEntry());
    }
}
