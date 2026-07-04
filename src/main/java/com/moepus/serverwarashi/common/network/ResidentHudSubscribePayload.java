package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.Serverwarashi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;


public record ResidentHudSubscribePayload(boolean on) implements CustomPacketPayload {
    public static final Type<ResidentHudSubscribePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Serverwarashi.MODID, "resident_hud_subscribe")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentHudSubscribePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    ResidentHudSubscribePayload::on,
                    ResidentHudSubscribePayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
