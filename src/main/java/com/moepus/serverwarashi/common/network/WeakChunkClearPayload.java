package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record WeakChunkClearPayload(String dimension,
                                    int chunkX,
                                    int chunkZ,
                                    WeakEntityType target) implements CustomPacketPayload {
    public static final Type<WeakChunkClearPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "clear_weak_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeakChunkClearPayload> STREAM_CODEC =
            StreamCodec.of(WeakChunkClearPayload::write, WeakChunkClearPayload::read);

    public WeakChunkClearPayload {
        if (dimension == null || ResourceLocation.tryParse(dimension) == null) {
            throw new IllegalArgumentException("Invalid dimension ID: " + dimension);
        }
        if (target == null) {
            throw new IllegalArgumentException("Weak clear target is required");
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WeakChunkClearPayload payload) {
        buffer.writeUtf(payload.dimension, PerfReportLimits.MAX_STRING_LENGTH);
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        WeakChunkPayload.writeType(buffer, payload.target);
    }

    private static WeakChunkClearPayload read(RegistryFriendlyByteBuf buffer) {
        return new WeakChunkClearPayload(
                buffer.readUtf(PerfReportLimits.MAX_STRING_LENGTH),
                buffer.readInt(), buffer.readInt(), WeakChunkPayload.readType(buffer));
    }
}
