package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record LoaderControlStatePayload(long reportId,
                                        ResourceLocation dimension,
                                        ResourceLocation controllerId,
                                        BlockPos ownerPos,
                                        boolean manuallyFrozen,
                                        boolean working,
                                        Status status) implements CustomPacketPayload {
    public static final Type<LoaderControlStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "loader_control_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoaderControlStatePayload> STREAM_CODEC =
            StreamCodec.of(LoaderControlStatePayload::write, LoaderControlStatePayload::read);

    public LoaderControlStatePayload {
        if (reportId <= 0L) {
            throw new IllegalArgumentException("Report ID must be positive");
        }
        dimension = Objects.requireNonNull(dimension, "dimension");
        controllerId = Objects.requireNonNull(controllerId, "controllerId");
        ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
        status = Objects.requireNonNull(status, "status");
    }

    public boolean hasAuthoritativeState() {
        return status == Status.SYNC || status == Status.APPLIED || status == Status.UNCHANGED;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, LoaderControlStatePayload payload) {
        buffer.writeVarLong(payload.reportId);
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeResourceLocation(payload.controllerId);
        buffer.writeBlockPos(payload.ownerPos);
        buffer.writeBoolean(payload.manuallyFrozen);
        buffer.writeBoolean(payload.working);
        buffer.writeVarInt(payload.status.ordinal());
    }

    private static LoaderControlStatePayload read(RegistryFriendlyByteBuf buffer) {
        long reportId = buffer.readVarLong();
        ResourceLocation dimension = buffer.readResourceLocation();
        ResourceLocation controllerId = buffer.readResourceLocation();
        BlockPos ownerPos = buffer.readBlockPos();
        boolean frozen = buffer.readBoolean();
        boolean working = buffer.readBoolean();
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= Status.values().length) {
            throw new IllegalArgumentException("Unknown loader control status: " + ordinal);
        }
        return new LoaderControlStatePayload(reportId, dimension, controllerId, ownerPos,
                frozen, working, Status.values()[ordinal]);
    }

    public enum Status {
        SYNC,
        APPLIED,
        UNCHANGED,
        STALE,
        MISSING,
        UNSUPPORTED,
        DENIED
    }
}
