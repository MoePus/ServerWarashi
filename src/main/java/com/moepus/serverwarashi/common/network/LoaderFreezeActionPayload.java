package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record LoaderFreezeActionPayload(long reportId,
                                        ResourceLocation dimension,
                                        ResourceLocation controllerId,
                                        BlockPos ownerPos,
                                        boolean frozen) implements CustomPacketPayload {
    public static final Type<LoaderFreezeActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "loader_freeze_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoaderFreezeActionPayload> STREAM_CODEC =
            StreamCodec.of(LoaderFreezeActionPayload::write, LoaderFreezeActionPayload::read);

    public LoaderFreezeActionPayload {
        if (reportId <= 0L) {
            throw new IllegalArgumentException("Report ID must be positive");
        }
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(controllerId, "controllerId");
        ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, LoaderFreezeActionPayload payload) {
        buffer.writeVarLong(payload.reportId);
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeResourceLocation(payload.controllerId);
        buffer.writeBlockPos(payload.ownerPos);
        buffer.writeBoolean(payload.frozen);
    }

    private static LoaderFreezeActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new LoaderFreezeActionPayload(buffer.readVarLong(), buffer.readResourceLocation(),
                buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readBoolean());
    }
}
