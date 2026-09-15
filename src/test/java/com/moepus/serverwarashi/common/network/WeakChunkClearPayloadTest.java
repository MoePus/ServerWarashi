package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.moepus.serverwarashi.testsupport.NetworkTestFixtures.buffer;
import static com.moepus.serverwarashi.testsupport.NetworkTestFixtures.roundTrip;

class WeakChunkClearPayloadTest {
    @Test
    void roundTripsTypedTarget() {
        var expected = new WeakChunkClearPayload("minecraft:overworld", 12, -34,
                new WeakEntityType(WeakEntityType.Kind.ITEM, "minecraft:cobblestone"));
        roundTrip(WeakChunkClearPayload.STREAM_CODEC, expected);
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

}
