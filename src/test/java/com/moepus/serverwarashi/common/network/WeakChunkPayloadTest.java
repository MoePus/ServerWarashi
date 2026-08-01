package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeakChunkPayloadTest {
    private static final WeakEntityType COW = entity("minecraft:cow");
    private static final WeakEntityType COBBLESTONE = item("minecraft:cobblestone");

    @Test
    void roundTripsSingleDimensionSnapshot() {
        WeakChunkPayload expected = new WeakChunkPayload(new WeakChunkSnapshot(
                "minecraft:overworld", 123_456L, List.of(
                new WeakChunkSnapshot.ChunkEntry(2, -3, 80,
                        Map.of(COW, 20, COBBLESTONE, 60)))));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            WeakChunkPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeakChunkPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void validatesSnapshotAndEntryBudgets() {
        var entry = new WeakChunkSnapshot.ChunkEntry(0, 0, 50, Map.of(COW, 50));
        assertThrows(IllegalArgumentException.class, () -> new WeakChunkSnapshot(
                "minecraft:overworld", 0L,
                Collections.nCopies(WeakChunkSnapshot.MAX_CHUNKS + 1, entry)));
        assertThrows(IllegalArgumentException.class,
                () -> new WeakChunkSnapshot.ChunkEntry(0, 0, 49, Map.of(COW, 49)));
        assertThrows(IllegalArgumentException.class,
                () -> new WeakChunkSnapshot.ChunkEntry(0, 0, 50, Map.of(COW, 49)));
    }

    @Test
    void keepsSameResourceIdDistinctAcrossKinds() {
        WeakEntityType item = item("test:shared");
        WeakEntityType entity = entity("test:shared");
        WeakChunkPayload expected = new WeakChunkPayload(new WeakChunkSnapshot(
                "minecraft:overworld", 1L, List.of(
                new WeakChunkSnapshot.ChunkEntry(0, 0, 50, Map.of(item, 20, entity, 30)))));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            WeakChunkPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeakChunkPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedChunkListOnDecode() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeUtf("minecraft:overworld");
            buffer.writeVarLong(0L);
            buffer.writeVarInt(WeakChunkSnapshot.MAX_CHUNKS + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> WeakChunkPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void networkProtocolIsVersionFour() {
        assertEquals("4", WarashiNetwork.NETWORK_VERSION);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static WeakEntityType item(String id) {
        return new WeakEntityType(WeakEntityType.Kind.ITEM, id);
    }

    private static WeakEntityType entity(String id) {
        return new WeakEntityType(WeakEntityType.Kind.ENTITY, id);
    }
}
