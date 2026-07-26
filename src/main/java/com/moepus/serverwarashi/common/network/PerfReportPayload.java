package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PerfReportPayload(PerfReportData report, Mode mode, boolean profiling)
        implements CustomPacketPayload {
    public static final Type<PerfReportPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "perf_report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PerfReportPayload> STREAM_CODEC =
            StreamCodec.of(PerfReportPayload::write, PerfReportPayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, PerfReportPayload payload) {
        PerfReportCodec.STREAM_CODEC.encode(buffer, payload.report);
        buffer.writeVarInt(payload.mode.ordinal());
        buffer.writeBoolean(payload.profiling);
    }

    private static PerfReportPayload read(RegistryFriendlyByteBuf buffer) {
        PerfReportData report = PerfReportCodec.STREAM_CODEC.decode(buffer);
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= Mode.values().length) {
            throw new IllegalArgumentException("Unknown perf payload mode: " + ordinal);
        }
        return new PerfReportPayload(report, Mode.values()[ordinal], buffer.readBoolean());
    }

    public enum Mode {
        DISPLAY,
        OPEN,
        UPDATE
    }
}
