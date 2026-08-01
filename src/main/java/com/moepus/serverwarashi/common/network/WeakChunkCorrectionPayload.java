package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record WeakChunkCorrectionPayload(
        String dimension,
        int chunkX,
        int chunkZ,
        long generatedAtEpochMillis,
        Optional<WeakChunkSnapshot.ChunkEntry> entry) implements CustomPacketPayload {
    public static final Type<WeakChunkCorrectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "weak_chunk_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeakChunkCorrectionPayload> STREAM_CODEC =
            StreamCodec.of(WeakChunkCorrectionPayload::write, WeakChunkCorrectionPayload::read);

    public WeakChunkCorrectionPayload {
        if (dimension == null || ResourceLocation.tryParse(dimension) == null) {
            throw new IllegalArgumentException("Invalid dimension ID: " + dimension);
        }
        if (generatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("Correction time must be non-negative");
        }
        entry = entry == null ? Optional.empty() : entry;
        entry.ifPresent(value -> {
            if (value.chunkX() != chunkX || value.chunkZ() != chunkZ) {
                throw new IllegalArgumentException("Correction entry position does not match target");
            }
        });
    }

    public WeakChunkCorrectionPayload(String dimension,
                                      int chunkX,
                                      int chunkZ,
                                      long generatedAtEpochMillis,
                                      WeakChunkSnapshot.ChunkEntry entry) {
        this(dimension, chunkX, chunkZ, generatedAtEpochMillis, Optional.ofNullable(entry));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WeakChunkCorrectionPayload payload) {
        buffer.writeUtf(payload.dimension, PerfReportLimits.MAX_STRING_LENGTH);
        buffer.writeInt(payload.chunkX);
        buffer.writeInt(payload.chunkZ);
        buffer.writeVarLong(payload.generatedAtEpochMillis);
        buffer.writeBoolean(payload.entry.isPresent());
        payload.entry.ifPresent(value -> WeakChunkPayload.writeChunk(buffer, value));
    }

    private static WeakChunkCorrectionPayload read(RegistryFriendlyByteBuf buffer) {
        String dimension = buffer.readUtf(PerfReportLimits.MAX_STRING_LENGTH);
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        long generatedAt = buffer.readVarLong();
        WeakChunkSnapshot.ChunkEntry entry = buffer.readBoolean()
                ? WeakChunkPayload.readChunk(buffer) : null;
        return new WeakChunkCorrectionPayload(dimension, chunkX, chunkZ, generatedAt, entry);
    }
}
