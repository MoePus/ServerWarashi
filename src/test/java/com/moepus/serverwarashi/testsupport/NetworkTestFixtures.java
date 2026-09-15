package com.moepus.serverwarashi.testsupport;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class NetworkTestFixtures {
    private NetworkTestFixtures() {
    }

    public static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    public static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T expected) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            codec.encode(buffer, expected);
            T decoded = codec.decode(buffer);
            assertEquals(expected, decoded);
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
