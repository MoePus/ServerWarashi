package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WeakChunkPayload(WeakChunkSnapshot snapshot,
                               long sweepId,
                               boolean sweepComplete) implements CustomPacketPayload {
    public static final Type<WeakChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "weak_chunks"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeakChunkPayload> STREAM_CODEC =
            StreamCodec.of(WeakChunkPayload::write, WeakChunkPayload::read);

    public WeakChunkPayload(WeakChunkSnapshot snapshot) {
        this(snapshot, 0L, true);
    }

    public WeakChunkPayload(WeakChunkSnapshot snapshot, long sweepId, boolean sweepComplete) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.sweepId = requireSweepId(sweepId);
        this.sweepComplete = sweepComplete;
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, WeakChunkPayload payload) {
        WeakChunkSnapshot snapshot = payload.snapshot;
        buffer.writeUtf(snapshot.dimension(), PerfReportLimits.MAX_STRING_LENGTH);
        buffer.writeVarLong(snapshot.generatedAtEpochMillis());
        buffer.writeVarInt(snapshot.chunks().size());
        snapshot.chunks().forEach(chunk -> writeChunk(buffer, chunk));
        buffer.writeVarLong(payload.sweepId);
        buffer.writeBoolean(payload.sweepComplete);
    }

    private static WeakChunkPayload read(RegistryFriendlyByteBuf buffer) {
        String dimension = buffer.readUtf(PerfReportLimits.MAX_STRING_LENGTH);
        long generatedAt = buffer.readVarLong();
        int size = readSize(buffer, WeakChunkSnapshot.MAX_CHUNKS, "weak chunk");
        List<WeakChunkSnapshot.ChunkEntry> chunks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            chunks.add(readChunk(buffer));
        }
        long sweepId = buffer.readVarLong();
        boolean sweepComplete = buffer.readBoolean();
        return new WeakChunkPayload(new WeakChunkSnapshot(dimension, generatedAt, chunks),
                sweepId, sweepComplete);
    }

    static void writeChunk(RegistryFriendlyByteBuf buffer, WeakChunkSnapshot.ChunkEntry chunk) {
        buffer.writeInt(chunk.chunkX());
        buffer.writeInt(chunk.chunkZ());
        buffer.writeVarInt(chunk.entityCount());
        buffer.writeVarInt(chunk.entityTypeCounts().size());
        chunk.entityTypeCounts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            writeType(buffer, entry.getKey());
            buffer.writeVarInt(entry.getValue());
        });
    }

    static WeakChunkSnapshot.ChunkEntry readChunk(RegistryFriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        int entityCount = buffer.readVarInt();
        int size = readSize(buffer, WeakChunkSnapshot.MAX_SAMPLED_TYPES, "entity type");
        Map<WeakEntityType, Integer> types = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++) {
            WeakEntityType type = readType(buffer);
            if (types.put(type, buffer.readVarInt()) != null) {
                throw new IllegalArgumentException("Duplicate sampled entity type: " + type);
            }
        }
        return new WeakChunkSnapshot.ChunkEntry(chunkX, chunkZ, entityCount, types);
    }

    static void writeType(RegistryFriendlyByteBuf buffer, WeakEntityType type) {
        buffer.writeVarInt(type.kind().ordinal());
        buffer.writeUtf(type.id(), PerfReportLimits.MAX_STRING_LENGTH);
    }

    static WeakEntityType readType(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= WeakEntityType.Kind.values().length) {
            throw new IllegalArgumentException("Unknown weak entity kind: " + ordinal);
        }
        return new WeakEntityType(WeakEntityType.Kind.values()[ordinal],
                buffer.readUtf(PerfReportLimits.MAX_STRING_LENGTH));
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " list size: " + size);
        }
        return size;
    }

    private static long requireSweepId(long sweepId) {
        if (sweepId < 0L) {
            throw new IllegalArgumentException("Sweep ID must be non-negative");
        }
        return sweepId;
    }
}
