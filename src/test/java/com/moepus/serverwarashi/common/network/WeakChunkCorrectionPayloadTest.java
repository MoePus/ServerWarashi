package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeakChunkCorrectionPayloadTest {
    private static final String DIMENSION = "minecraft:overworld";

    @Test
    void roundTripsUpsertAndRemoval() {
        assertRoundTrip(new WeakChunkCorrectionPayload(DIMENSION, 2, -3, 10L, entry(2, -3, 80)));
        assertRoundTrip(new WeakChunkCorrectionPayload(
                DIMENSION, 2, -3, 11L, Optional.empty()));
    }

    @Test
    void rejectsEntryForDifferentChunk() {
        assertThrows(IllegalArgumentException.class, () -> new WeakChunkCorrectionPayload(
                DIMENSION, 2, -3, 10L, entry(3, -3, 80)));
    }

    private static void assertRoundTrip(WeakChunkCorrectionPayload expected) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            WeakChunkCorrectionPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeakChunkCorrectionPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static WeakChunkSnapshot.ChunkEntry entry(int x, int z, int count) {
        var type = new WeakEntityType(WeakEntityType.Kind.ENTITY, "minecraft:cow");
        return new WeakChunkSnapshot.ChunkEntry(x, z, count, Map.of(type, count));
    }
}
