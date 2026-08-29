package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PerfWorldHotspotPayload(long streamEpoch,
                                      long baseRevision,
                                      long revision,
                                      long tickCount,
                                      long nanosPerSample,
                                      ResourceLocation dimension,
                                      boolean full,
                                      List<Entry> upserts,
                                      List<PerfWorldHotspotIndex.Key> removals)
        implements CustomPacketPayload {
    public static final Type<PerfWorldHotspotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "perf_world_hotspot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfWorldHotspotPayload> STREAM_CODEC =
            StreamCodec.of(PerfWorldHotspotPayload::write, PerfWorldHotspotPayload::read);

    public PerfWorldHotspotPayload {
        if (streamEpoch < 0L || baseRevision < 0L || revision < 0L
                || tickCount < 0L || nanosPerSample < 0L) {
            throw new IllegalArgumentException("Hotspot payload scalars must be non-negative");
        }
        if (full && baseRevision != 0L) {
            throw new IllegalArgumentException("Full hotspot payload must use base revision zero");
        }
        if (!full && revision <= baseRevision) {
            throw new IllegalArgumentException("Hotspot delta revision must advance");
        }
        dimension = Objects.requireNonNull(dimension, "dimension");
        upserts = List.copyOf(upserts);
        removals = List.copyOf(removals);
        validateSize(upserts.size());
        validateSize(removals.size());
        if (upserts.stream().anyMatch(entry -> entry.measurementStartTick() > tickCount)) {
            throw new IllegalArgumentException("Hotspot measurement starts after the payload");
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, PerfWorldHotspotPayload payload) {
        buffer.writeVarLong(payload.streamEpoch);
        buffer.writeVarLong(payload.baseRevision);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.tickCount);
        buffer.writeVarLong(payload.nanosPerSample);
        buffer.writeResourceLocation(payload.dimension);
        buffer.writeBoolean(payload.full);
        buffer.writeVarInt(payload.upserts.size());
        payload.upserts.forEach(entry -> writeEntry(buffer, entry));
        buffer.writeVarInt(payload.removals.size());
        payload.removals.forEach(key -> writeKey(buffer, key));
    }

    private static PerfWorldHotspotPayload read(RegistryFriendlyByteBuf buffer) {
        long streamEpoch = buffer.readVarLong();
        long baseRevision = buffer.readVarLong();
        long revision = buffer.readVarLong();
        long tickCount = buffer.readVarLong();
        long nanosPerSample = buffer.readVarLong();
        ResourceLocation dimension = buffer.readResourceLocation();
        boolean full = buffer.readBoolean();
        List<Entry> upserts = readList(buffer, () -> readEntry(buffer));
        List<PerfWorldHotspotIndex.Key> removals = readList(buffer, () -> readKey(buffer));
        return new PerfWorldHotspotPayload(streamEpoch, baseRevision, revision, tickCount,
                nanosPerSample, dimension, full, upserts, removals);
    }

    private static void writeEntry(RegistryFriendlyByteBuf buffer, Entry entry) {
        writeKey(buffer, entry.key);
        buffer.writeByte(entry.detailKind.ordinal());
        buffer.writeVarInt(entry.registryId);
        buffer.writeVarLong(entry.measurementStartTick);
        buffer.writeVarLong(entry.samples);
        buffer.writeVarInt(entry.maxSamplesQ8);
    }

    private static Entry readEntry(RegistryFriendlyByteBuf buffer) {
        PerfWorldHotspotIndex.Key key = readKey(buffer);
        int detailOrdinal = buffer.readUnsignedByte();
        TickDetailKind[] kinds = TickDetailKind.values();
        if (detailOrdinal >= kinds.length) {
            throw new IllegalArgumentException("Unknown hotspot detail kind: " + detailOrdinal);
        }
        return new Entry(key, kinds[detailOrdinal], buffer.readVarInt(),
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
    }

    private static void writeKey(
            RegistryFriendlyByteBuf buffer, PerfWorldHotspotIndex.Key key) {
        buffer.writeByte(key.kind().ordinal());
        if (key.kind() == PerfWorldHotspotIndex.Kind.ENTITY) {
            buffer.writeVarInt(key.entityId());
        } else {
            buffer.writeLong(key.blockPosition());
        }
    }

    private static PerfWorldHotspotIndex.Key readKey(RegistryFriendlyByteBuf buffer) {
        int kind = buffer.readUnsignedByte();
        if (kind == PerfWorldHotspotIndex.Kind.ENTITY.ordinal()) {
            return PerfWorldHotspotIndex.Key.entity(buffer.readVarInt());
        }
        if (kind == PerfWorldHotspotIndex.Kind.BLOCK_ENTITY.ordinal()) {
            return new PerfWorldHotspotIndex.Key(
                    PerfWorldHotspotIndex.Kind.BLOCK_ENTITY, -1, buffer.readLong());
        }
        throw new IllegalArgumentException("Unknown hotspot object kind: " + kind);
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buffer, Reader<T> reader) {
        int size = buffer.readVarInt();
        validateSize(size);
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(reader.read());
        }
        return List.copyOf(result);
    }

    private static void validateSize(int size) {
        if (size < 0 || size > PerfReportLimits.MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid hotspot entry count: " + size);
        }
    }

    public record Entry(PerfWorldHotspotIndex.Key key,
                        TickDetailKind detailKind,
                        int registryId,
                        long measurementStartTick,
                        long samples,
                        int maxSamplesQ8) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(detailKind, "detailKind");
            if (registryId < 0 || measurementStartTick <= 0L
                    || samples < 0L || maxSamplesQ8 < 0) {
                throw new IllegalArgumentException("Invalid compact hotspot entry");
            }
            key.validateDetailKind(detailKind);
        }
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read();
    }
}
