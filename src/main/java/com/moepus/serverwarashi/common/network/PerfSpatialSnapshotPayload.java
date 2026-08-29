package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportProjection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PerfSpatialSnapshotPayload(PerfReportData report, boolean profiling)
        implements CustomPacketPayload {
    public static final Type<PerfSpatialSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "perf_spatial_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfSpatialSnapshotPayload>
            STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                PerfReportCodec.STREAM_CODEC.encode(buffer, payload.report);
                buffer.writeBoolean(payload.profiling);
            },
            buffer -> new PerfSpatialSnapshotPayload(
                    PerfReportCodec.STREAM_CODEC.decode(buffer), buffer.readBoolean()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static PerfSpatialSnapshotPayload forMap(
            PerfReportData report, boolean profiling) {
        return new PerfSpatialSnapshotPayload(PerfReportProjection.forMap(report), profiling);
    }

    public static PerfSpatialSnapshotPayload forWorld(
            PerfReportData report, boolean profiling) {
        return new PerfSpatialSnapshotPayload(PerfReportProjection.forWorld(report), profiling);
    }
}
