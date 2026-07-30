package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.WeakChunkClearPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class WeakChunkClearClient {
    private WeakChunkClearClient() {
    }

    public static boolean canSend() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null
                && NetworkRegistry.hasChannel(connection, WeakChunkClearPayload.TYPE.id());
    }

    public static boolean send(WeakChunkClearPayload payload) {
        if (!canSend()) {
            return false;
        }
        PacketDistributor.sendToServer(payload);
        return true;
    }
}
