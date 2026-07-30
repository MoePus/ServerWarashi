package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeakChunkClearPayloadTest {
    @Test
    void roundTripsTypedTarget() {
        var expected = new WeakChunkClearPayload("minecraft:overworld", 12, -34,
                new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone"));
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            WeakChunkClearPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, WeakChunkClearPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnknownTargetKind() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeUtf("minecraft:overworld");
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeVarInt(WeakEntityType.Kind.values().length);
            assertThrows(IllegalArgumentException.class,
                    () -> WeakChunkClearPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
