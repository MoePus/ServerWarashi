package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PerfScreenSubscriptionPayload(boolean open) implements CustomPacketPayload {
    public static final Type<PerfScreenSubscriptionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    ServerWarashi.MODID, "perf_screen_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfScreenSubscriptionPayload>
            STREAM_CODEC = StreamCodec.of(PerfScreenSubscriptionPayload::write,
                    PerfScreenSubscriptionPayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
            RegistryFriendlyByteBuf buffer, PerfScreenSubscriptionPayload payload) {
        buffer.writeBoolean(payload.open);
    }

    private static PerfScreenSubscriptionPayload read(RegistryFriendlyByteBuf buffer) {
        return new PerfScreenSubscriptionPayload(buffer.readBoolean());
    }
}
