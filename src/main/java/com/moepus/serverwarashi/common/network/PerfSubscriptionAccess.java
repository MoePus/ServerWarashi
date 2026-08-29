package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class PerfSubscriptionAccess {
    private PerfSubscriptionAccess() {
    }

    static boolean isPermitted(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        boolean singleplayer = server != null && server.isSingleplayer();
        return PerfAccessPolicy.canSubscribe(player.hasPermissions(2),
                TicketPerfConfig.allowNonOpSubscriptions(), singleplayer);
    }
}
