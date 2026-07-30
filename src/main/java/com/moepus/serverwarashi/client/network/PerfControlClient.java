package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.common.network.PerfControlPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class PerfControlClient {
    private PerfControlClient() {
    }

    public static boolean send(PerfControlPayload.Action action) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(connection, PerfControlPayload.TYPE.id())) {
            return false;
        }
        PacketDistributor.sendToServer(new PerfControlPayload(action));
        return true;
    }
}
