package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.Serverwarashi;
import com.moepus.serverwarashi.common.report.TableReport;
import com.moepus.serverwarashi.common.report.TableReportCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;


public record ReportPayload(ReportKind kind, TableReport report) implements CustomPacketPayload {
    public static final Type<ReportPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Serverwarashi.MODID, "report")
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, ReportKind> KIND_CODEC = StreamCodec.of(
            (buffer, kind) -> buffer.writeVarInt(kind.ordinal()),
            ReportPayload::readKind
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ReportPayload> STREAM_CODEC = StreamCodec.composite(
            KIND_CODEC,
            ReportPayload::kind,
            TableReportCodec.STREAM_CODEC,
            ReportPayload::report,
            ReportPayload::new
    );

    private static ReportKind readKind(RegistryFriendlyByteBuf buffer) {
        // 校验 ordinal 合法性，防止越界
        int ordinal = buffer.readVarInt();
        ReportKind[] values = ReportKind.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown report kind: " + ordinal);
        }
        return values[ordinal];
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ReportKind {
        PERF_REPORT,
        RESIDENT_COST
    }
}
