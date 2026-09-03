package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackClusterer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public record UnknownStackDetailsPayload(long requestId,
                                         long reportId,
                                         long reservoirRevision,
                                         Status status,
                                         long totalObserved,
                                         int retainedCount,
                                         List<Cluster> average)
        implements CustomPacketPayload {
    static final int MAX_TOTAL_TEXT_BYTES = 64 * 1_024;
    static final int MAX_STACK_FRAMES = 96;
    public static final Type<UnknownStackDetailsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "unknown_stack_details"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownStackDetailsPayload>
            STREAM_CODEC = StreamCodec.of(UnknownStackDetailsPayload::write,
            UnknownStackDetailsPayload::read);

    public UnknownStackDetailsPayload {
        Objects.requireNonNull(status, "status");
        PreparedClusterList preparedAverage = prepared(average);
        boolean prevalidated = preparedAverage != null;
        average = prevalidated ? preparedAverage.values : List.copyOf(average);
        var metadata = new Metadata(requestId, reportId, reservoirRevision,
                status, totalObserved, retainedCount);
        validate(metadata, average, prevalidated,
                prevalidated ? preparedAverage.textBytes : 0);
    }

    public static UnknownStackDetailsPayload failed(
            long requestId, long reportId, Status status) {
        if (status == Status.OK) {
            throw new IllegalArgumentException("OK is not a failure status");
        }
        return new UnknownStackDetailsPayload(requestId, reportId, 0L, status,
                0L, 0, List.of());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, UnknownStackDetailsPayload payload) {
        buffer.writeVarLong(payload.requestId);
        buffer.writeVarLong(payload.reportId);
        buffer.writeVarLong(payload.reservoirRevision);
        buffer.writeVarInt(payload.status.ordinal());
        buffer.writeVarLong(payload.totalObserved);
        buffer.writeVarInt(payload.retainedCount);
        writeClusters(buffer, payload.average);
    }

    private static UnknownStackDetailsPayload read(RegistryFriendlyByteBuf buffer) {
        long requestId = buffer.readVarLong();
        long reportId = buffer.readVarLong();
        long revision = buffer.readVarLong();
        Status status = readStatus(buffer);
        long totalObserved = buffer.readVarLong();
        int retainedCount = buffer.readVarInt();
        List<Cluster> average = readClusters(buffer);
        return new UnknownStackDetailsPayload(requestId, reportId, revision,
                status, totalObserved, retainedCount, average);
    }

    private static void writeClusters(RegistryFriendlyByteBuf buffer, List<Cluster> clusters) {
        buffer.writeVarInt(clusters.size());
        for (Cluster cluster : clusters) {
            buffer.writeUtf(cluster.anchor, UnknownStackClusterer.MAX_FRAME_TEXT_LENGTH);
            buffer.writeVarInt(cluster.sampleCount);
            buffer.writeVarInt(cluster.representativeCount);
            buffer.writeDouble(cluster.estimatedAverageMillis);
            buffer.writeVarInt(cluster.frames.size());
            for (String frame : cluster.frames) {
                buffer.writeUtf(frame, UnknownStackClusterer.MAX_FRAME_TEXT_LENGTH);
            }
        }
    }

    private static List<Cluster> readClusters(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, UnknownStackClusterer.TOP_LIMIT, "cluster list");
        List<Cluster> clusters = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String anchor = buffer.readUtf(UnknownStackClusterer.MAX_FRAME_TEXT_LENGTH);
            int samples = buffer.readVarInt();
            int representative = buffer.readVarInt();
            double averageMillis = buffer.readDouble();
            int frameCount = readSize(buffer, MAX_STACK_FRAMES, "stack frame list");
            List<String> frames = new ArrayList<>(frameCount);
            for (int frame = 0; frame < frameCount; frame++) {
                frames.add(buffer.readUtf(UnknownStackClusterer.MAX_FRAME_TEXT_LENGTH));
            }
            clusters.add(new Cluster(anchor, samples, representative,
                    averageMillis, frames));
        }
        return List.copyOf(clusters);
    }

    private static Status readStatus(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= Status.values().length) {
            throw new IllegalArgumentException("Unknown stack response status: " + ordinal);
        }
        return Status.values()[ordinal];
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, int limit, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > limit) {
            throw new IllegalArgumentException("Invalid " + name + " size: " + size);
        }
        return size;
    }

    static List<Cluster> prepareClusters(List<Cluster> clusters) {
        List<Cluster> values = List.copyOf(clusters);
        if (values.size() > UnknownStackClusterer.TOP_LIMIT) {
            throw new IllegalArgumentException("Too many Unknown stack clusters");
        }
        return new PreparedClusterList(values, validateClusters(values));
    }

    private static PreparedClusterList prepared(List<Cluster> clusters) {
        return clusters instanceof PreparedClusterList prepared ? prepared : null;
    }

    private static void validate(Metadata metadata,
                                 List<Cluster> average,
                                 boolean prevalidated,
                                 int preparedTextBytes) {
        if (metadata.requestId <= 0L || metadata.reportId <= 0L || metadata.revision < 0L
                || metadata.totalObserved < 0L || metadata.retainedCount < 0
                || metadata.retainedCount > 256
                || metadata.totalObserved < metadata.retainedCount) {
            throw new IllegalArgumentException("Invalid Unknown stack response metadata");
        }
        if (average.size() > UnknownStackClusterer.TOP_LIMIT) {
            throw new IllegalArgumentException("Too many Unknown stack clusters");
        }
        if (metadata.status != Status.OK && !average.isEmpty()) {
            throw new IllegalArgumentException("Failure response cannot contain clusters");
        }
        int textBytes = prevalidated ? preparedTextBytes
                : validateClusters(average);
        if (textBytes > MAX_TOTAL_TEXT_BYTES) {
            throw new IllegalArgumentException("Unknown stack response text exceeds 64 KiB");
        }
    }

    private static int validateClusters(List<Cluster> clusters) {
        int bytes = 0;
        for (Cluster cluster : clusters) {
            bytes = addTextBytes(bytes, cluster.anchor);
            if (cluster.frames.size() > MAX_STACK_FRAMES) {
                throw new IllegalArgumentException("Unknown representative stack is too deep");
            }
            for (String frame : cluster.frames) {
                bytes = addTextBytes(bytes, frame);
            }
        }
        return bytes;
    }

    private static int addTextBytes(int current, String value) {
        Objects.requireNonNull(value, "stack text");
        if (value.length() > UnknownStackClusterer.MAX_FRAME_TEXT_LENGTH) {
            throw new IllegalArgumentException("Unknown stack text is too long");
        }
        long total = current + (long) utf8Length(value);
        if (total > MAX_TOTAL_TEXT_BYTES) {
            throw new IllegalArgumentException("Unknown stack response text exceeds 64 KiB");
        }
        return (int) total;
    }

    static int utf8Length(String value) {
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x7F) {
                bytes++;
            } else if (character <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(character)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    public enum Status {
        OK,
        STALE,
        EMPTY,
        DENIED,
        BUSY,
        ERROR
    }

    private record Metadata(long requestId,
                            long reportId,
                            long revision,
                            Status status,
                            long totalObserved,
                            int retainedCount) {
    }

    private static final class PreparedClusterList extends AbstractList<Cluster>
            implements RandomAccess {
        private final List<Cluster> values;
        private final int textBytes;

        private PreparedClusterList(List<Cluster> values, int textBytes) {
            this.values = values;
            this.textBytes = textBytes;
        }

        @Override
        public Cluster get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    public record Cluster(String anchor,
                          int sampleCount,
                          int representativeCount,
                          double estimatedAverageMillis,
                          List<String> frames) {
        public Cluster {
            Objects.requireNonNull(anchor, "anchor");
            frames = List.copyOf(frames);
            if (sampleCount <= 0 || representativeCount <= 0
                    || representativeCount > sampleCount
                    || !Double.isFinite(estimatedAverageMillis) || estimatedAverageMillis < 0.0D) {
                throw new IllegalArgumentException("Invalid Unknown stack cluster");
            }
        }
    }
}
