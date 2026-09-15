package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.moepus.serverwarashi.testsupport.NetworkTestFixtures.roundTrip;

class WeakChunkCorrectionPayloadTest {
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void roundTripsUpsertAndRemoval() {
        roundTrip(WeakChunkCorrectionPayload.STREAM_CODEC,
                new WeakChunkCorrectionPayload(DIMENSION, 2, -3, 10L, entry(2, -3, 80)));
        roundTrip(WeakChunkCorrectionPayload.STREAM_CODEC, new WeakChunkCorrectionPayload(
                DIMENSION, 2, -3, 11L, Optional.empty()));
    }

    @Test
    void rejectsEntryForDifferentChunk() {
        assertThrows(IllegalArgumentException.class, () -> new WeakChunkCorrectionPayload(
                DIMENSION, 2, -3, 10L, entry(3, -3, 80)));
    }

    private static WeakChunkSnapshot.ChunkEntry entry(int x, int z, int count) {
        var type = new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");
        return new WeakChunkSnapshot.ChunkEntry(x, z, count, Map.of(type, count));
    }
}
