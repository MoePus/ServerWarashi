package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PerfControlPayload(Action action) implements CustomPacketPayload {
    public static final Type<PerfControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "perf_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfControlPayload> STREAM_CODEC =
            StreamCodec.of(PerfControlPayload::write, PerfControlPayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, PerfControlPayload payload) {
        buffer.writeVarInt(payload.action.ordinal());
    }

    private static PerfControlPayload read(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= Action.values().length) {
            throw new IllegalArgumentException("Unknown perf control action: " + ordinal);
        }
        return new PerfControlPayload(Action.values()[ordinal]);
    }

    public enum Action {
        TOGGLE,
        CLEAR
    }
}
