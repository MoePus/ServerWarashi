package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PerfMapSubscriptionPayload(boolean mapOpen, boolean worldHotspots)
        implements CustomPacketPayload {
    public static final Type<PerfMapSubscriptionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "perf_map_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfMapSubscriptionPayload> STREAM_CODEC =
            StreamCodec.of(PerfMapSubscriptionPayload::write, PerfMapSubscriptionPayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, PerfMapSubscriptionPayload payload) {
        buffer.writeBoolean(payload.mapOpen);
        buffer.writeBoolean(payload.worldHotspots);
    }

    private static PerfMapSubscriptionPayload read(RegistryFriendlyByteBuf buffer) {
        return new PerfMapSubscriptionPayload(buffer.readBoolean(), buffer.readBoolean());
    }

    public boolean subscribed() {
        return mapOpen || worldHotspots;
    }
}
