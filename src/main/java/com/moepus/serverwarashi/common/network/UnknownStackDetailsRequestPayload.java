package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.ServerWarashi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record UnknownStackDetailsRequestPayload(long requestId, long reportId)
        implements CustomPacketPayload {
    public static final Type<UnknownStackDetailsRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ServerWarashi.MODID, "unknown_stack_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnknownStackDetailsRequestPayload>
            STREAM_CODEC = StreamCodec.of(
            UnknownStackDetailsRequestPayload::write, UnknownStackDetailsRequestPayload::read);

    public UnknownStackDetailsRequestPayload {
        if (requestId <= 0L || reportId <= 0L) {
            throw new IllegalArgumentException("Unknown stack request IDs must be positive");
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer,
                              UnknownStackDetailsRequestPayload payload) {
        buffer.writeVarLong(payload.requestId);
        buffer.writeVarLong(payload.reportId);
    }

    private static UnknownStackDetailsRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new UnknownStackDetailsRequestPayload(buffer.readVarLong(), buffer.readVarLong());
    }
}
