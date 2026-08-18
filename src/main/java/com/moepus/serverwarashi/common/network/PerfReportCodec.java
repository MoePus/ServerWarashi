package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportLimits;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public final class PerfReportCodec {
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfReportData> STREAM_CODEC =
            StreamCodec.of(PerfReportCodec::write, PerfReportCodec::read);

    private PerfReportCodec() {
    }

    private static void write(RegistryFriendlyByteBuf buffer, PerfReportData report) {
        EntryBudget budget = new EntryBudget();
        buffer.writeVarLong(report.reportId());
        buffer.writeDouble(report.serverMspt());
        buffer.writeVarLong(report.lastTickGcMillis());
        buffer.writeVarLong(report.elapsedMillis());
        buffer.writeVarLong(report.generatedAtEpochMillis());
        buffer.writeVarLong(report.tickCount());
        buffer.writeVarLong(report.totalSamples());
        buffer.writeVarInt(report.intervalMicros());
        buffer.writeVarInt(report.omittedChunks());
        buffer.writeVarInt(report.omittedTickables());
        buffer.writeVarInt(report.omittedOwnerSources());
        buffer.writeVarInt(report.omittedLoaderSources());
        writeList(buffer, report.chunks(), PerfReportCodec::writeChunk, budget);
        writeList(buffer, report.tickables(), PerfReportCodec::writeTickable, budget);
        writeList(buffer, report.owners(), PerfReportCodec::writeOwner, budget);
        PerfLoaderReportCodec.write(buffer, report.loaderSources(), budget);
    }

    private static PerfReportData read(RegistryFriendlyByteBuf buffer) {
        EntryBudget budget = new EntryBudget();
        long reportId = buffer.readVarLong();
        double serverMspt = buffer.readDouble();
        long lastTickGcMillis = buffer.readVarLong();
        long elapsedMillis = buffer.readVarLong();
        long generatedAtEpochMillis = buffer.readVarLong();
        long tickCount = buffer.readVarLong();
        long totalSamples = buffer.readVarLong();
        int intervalMicros = buffer.readVarInt();
        int omittedChunks = buffer.readVarInt();
        int omittedTickables = buffer.readVarInt();
        int omittedOwnerSources = buffer.readVarInt();
        int omittedLoaderSources = buffer.readVarInt();
        List<PerfReportData.ChunkEntry> chunks = readList(buffer, PerfReportCodec::readChunk, budget);
        List<PerfReportData.TickableEntry> tickables = readList(buffer, PerfReportCodec::readTickable, budget);
        List<PerfReportData.OwnerEntry> owners = readList(buffer, PerfReportCodec::readOwner, budget);
        List<PerfReportData.LoaderSourceEntry> loaders = PerfLoaderReportCodec.read(buffer, budget);
        return new PerfReportData(reportId, serverMspt, lastTickGcMillis, elapsedMillis, generatedAtEpochMillis,
                tickCount, totalSamples, intervalMicros, omittedChunks, omittedTickables,
                omittedOwnerSources, omittedLoaderSources, chunks, tickables, owners, loaders);
    }

    private static void writeChunk(RegistryFriendlyByteBuf buffer,
                                   PerfReportData.ChunkEntry entry,
                                   EntryBudget budget) {
        writeString(buffer, entry.dimension(), budget);
        buffer.writeInt(entry.chunkX());
        buffer.writeInt(entry.chunkZ());
        buffer.writeVarInt(entry.entityCount());
        buffer.writeVarInt(entry.blockEntityCount());
        writeList(buffer, entry.topEntities(), PerfReportCodec::writeObjectCount, budget);
        writeList(buffer, entry.topBlockEntities(), PerfReportCodec::writeObjectCount, budget);
        writeMetric(buffer, entry.metric());
    }

    private static PerfReportData.ChunkEntry readChunk(RegistryFriendlyByteBuf buffer, EntryBudget budget) {
        return new PerfReportData.ChunkEntry(readString(buffer, budget), buffer.readInt(), buffer.readInt(),
                buffer.readVarInt(), buffer.readVarInt(),
                readList(buffer, PerfReportCodec::readObjectCount, budget),
                readList(buffer, PerfReportCodec::readObjectCount, budget), readMetric(buffer));
    }

    private static void writeObjectCount(RegistryFriendlyByteBuf buffer,
                                         PerfReportData.ObjectCount entry,
                                         EntryBudget budget) {
        writeString(buffer, entry.className(), budget);
        writeString(buffer, entry.detailId(), budget);
        buffer.writeVarInt(entry.detailKind().ordinal());
        buffer.writeVarInt(entry.count());
        buffer.writeInt(entry.x());
        buffer.writeInt(entry.y());
        buffer.writeInt(entry.z());
    }

    private static PerfReportData.ObjectCount readObjectCount(RegistryFriendlyByteBuf buffer,
                                                               EntryBudget budget) {
        return new PerfReportData.ObjectCount(readString(buffer, budget), readString(buffer, budget),
                readEnum(buffer, TickDetailKind.values(), "detail kind"), buffer.readVarInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    private static void writeTickable(RegistryFriendlyByteBuf buffer,
                                      PerfReportData.TickableEntry entry,
                                      EntryBudget budget) {
        buffer.writeVarInt(entry.type().ordinal());
        writeString(buffer, entry.dimension(), budget);
        writeString(buffer, entry.className(), budget);
        writeString(buffer, entry.detailId(), budget);
        buffer.writeVarInt(entry.detailKind().ordinal());
        buffer.writeVarInt(entry.entityId());
        buffer.writeBoolean(entry.positioned());
        buffer.writeInt(entry.x());
        buffer.writeInt(entry.y());
        buffer.writeInt(entry.z());
        buffer.writeBoolean(entry.chunkPosition());
        writeString(buffer, entry.eventLoop(), budget);
        writeMetric(buffer, entry.metric());
    }

    private static PerfReportData.TickableEntry readTickable(RegistryFriendlyByteBuf buffer,
                                                              EntryBudget budget) {
        TickType type = readEnum(buffer, TickType.values(), "tick type");
        String dimension = readString(buffer, budget);
        String className = readString(buffer, budget);
        String detailId = readString(buffer, budget);
        TickDetailKind detailKind = readEnum(buffer, TickDetailKind.values(), "detail kind");
        int entityId = buffer.readVarInt();
        boolean positioned = buffer.readBoolean();
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        boolean chunkPosition = buffer.readBoolean();
        String eventLoop = readString(buffer, budget);
        return new PerfReportData.TickableEntry(type, dimension, className, detailId, detailKind,
                entityId, positioned,
                x, y, z, chunkPosition, eventLoop, readMetric(buffer));
    }

    private static void writeOwner(RegistryFriendlyByteBuf buffer,
                                   PerfReportData.OwnerEntry entry,
                                   EntryBudget budget) {
        buffer.writeVarInt(entry.kind().ordinal());
        writeString(buffer, entry.id(), budget);
        writeString(buffer, entry.name(), budget);
        buffer.writeBoolean(entry.online());
        buffer.writeVarLong(entry.lastLoginEpochMillis());
        writePosition(buffer, entry.position(), budget);
        buffer.writeVarInt(entry.chunkCount());
        writeMetric(buffer, entry.metric());
        writeList(buffer, entry.sources(), PerfReportCodec::writeOwnerSource, budget);
    }

    private static PerfReportData.OwnerEntry readOwner(RegistryFriendlyByteBuf buffer, EntryBudget budget) {
        var kind = readEnum(buffer, PerfReportData.AttributionKind.values(), "attribution kind");
        String id = readString(buffer, budget);
        String name = readString(buffer, budget);
        boolean online = buffer.readBoolean();
        long lastLoginEpochMillis = buffer.readVarLong();
        PerfReportData.Position position = readPosition(buffer, budget);
        int chunkCount = buffer.readVarInt();
        PerfReportData.Metric metric = readMetric(buffer);
        return new PerfReportData.OwnerEntry(kind, id, name, online, lastLoginEpochMillis,
                position, chunkCount, metric, readList(buffer, PerfReportCodec::readOwnerSource, budget));
    }

    private static void writeOwnerSource(RegistryFriendlyByteBuf buffer,
                                         PerfReportData.OwnerSourceEntry entry,
                                         EntryBudget budget) {
        writeString(buffer, entry.id(), budget);
        writeString(buffer, entry.name(), budget);
        writePosition(buffer, entry.position(), budget);
        buffer.writeVarInt(entry.chunkCount());
        writeMetric(buffer, entry.metric());
    }

    private static PerfReportData.OwnerSourceEntry readOwnerSource(RegistryFriendlyByteBuf buffer,
                                                                   EntryBudget budget) {
        return new PerfReportData.OwnerSourceEntry(readString(buffer, budget), readString(buffer, budget),
                readPosition(buffer, budget),
                buffer.readVarInt(), readMetric(buffer));
    }

    private static void writePosition(RegistryFriendlyByteBuf buffer,
                                      PerfReportData.Position position,
                                      EntryBudget budget) {
        buffer.writeBoolean(position != null);
        if (position != null) {
            writeString(buffer, position.dimension(), budget);
            buffer.writeInt(position.x());
            buffer.writeInt(position.y());
            buffer.writeInt(position.z());
        }
    }

    private static PerfReportData.Position readPosition(RegistryFriendlyByteBuf buffer, EntryBudget budget) {
        return buffer.readBoolean()
                ? new PerfReportData.Position(readString(buffer, budget),
                buffer.readInt(), buffer.readInt(), buffer.readInt())
                : null;
    }

    static void writeString(RegistryFriendlyByteBuf buffer, String value, EntryBudget budget) {
        budget.claimString(value);
        buffer.writeUtf(value, PerfReportLimits.MAX_STRING_LENGTH);
    }

    static String readString(RegistryFriendlyByteBuf buffer, EntryBudget budget) {
        String value = buffer.readUtf(PerfReportLimits.MAX_STRING_LENGTH);
        budget.claimString(value);
        return value;
    }

    private static void writeMetric(RegistryFriendlyByteBuf buffer, PerfReportData.Metric metric) {
        buffer.writeDouble(metric.mspt());
        buffer.writeDouble(metric.max());
        buffer.writeVarLong(metric.samples());
    }

    private static PerfReportData.Metric readMetric(RegistryFriendlyByteBuf buffer) {
        return new PerfReportData.Metric(buffer.readDouble(), buffer.readDouble(), buffer.readVarLong());
    }

    private static <T> void writeList(RegistryFriendlyByteBuf buffer,
                                      List<T> values,
                                      Writer<T> writer,
                                      EntryBudget budget) {
        requireSize(values.size(), PerfReportLimits.MAX_ENTRIES, "report");
        budget.claimEntries(values.size());
        buffer.writeVarInt(values.size());
        values.forEach(value -> writer.write(buffer, value, budget));
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buffer,
                                        Reader<T> reader,
                                        EntryBudget budget) {
        int size = readSize(buffer, PerfReportLimits.MAX_ENTRIES, "report", budget);
        List<T> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(reader.read(buffer, budget));
        }
        return List.copyOf(result);
    }

    static int readSize(RegistryFriendlyByteBuf buffer,
                        int maximum,
                        String label,
                        EntryBudget budget) {
        int size = buffer.readVarInt();
        requireSize(size, maximum, label);
        budget.claimEntries(size);
        return size;
    }

    static void requireSize(int size, int maximum, String label) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " list size: " + size);
        }
    }

    private static <T> T readEnum(RegistryFriendlyByteBuf buffer, T[] values, String label) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown " + label + ": " + ordinal);
        }
        return values[ordinal];
    }

    static final class EntryBudget {
        private int entries;
        private int loaderRegions;
        private int stringChars;

        void claimEntries(int count) {
            entries += count;
            if (entries > PerfReportLimits.MAX_CODEC_ENTRIES) {
                throw new IllegalArgumentException("Perf report exceeds total entry budget");
            }
        }

        void claimLoaderRegions(int count) {
            loaderRegions += count;
            if (loaderRegions > PerfReportLimits.MAX_LOADER_REGIONS) {
                throw new IllegalArgumentException("Perf report exceeds loader region budget");
            }
        }

        void claimString(String value) {
            stringChars += value.length();
            if (stringChars > PerfReportLimits.MAX_CODEC_STRING_CHARS) {
                throw new IllegalArgumentException("Perf report exceeds string budget");
            }
        }
    }

    @FunctionalInterface
    private interface Writer<T> {
        void write(RegistryFriendlyByteBuf buffer, T value, EntryBudget budget);
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(RegistryFriendlyByteBuf buffer, EntryBudget budget);
    }
}
