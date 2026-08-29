package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class PerfSubscriptionAccess {
    private PerfSubscriptionAccess() {
    }

    static boolean isPermitted(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        boolean singleplayerOwner = server != null
                && server.isSingleplayerOwner(player.getGameProfile());
        return isPermitted(player.hasPermissions(2),
                TicketPerfConfig.allowNonOpSubscriptions(), singleplayerOwner);
    }

    static boolean isPermitted(boolean operator,
                               boolean allowNonOperators,
                               boolean singleplayerOwner) {
        return operator || allowNonOperators || singleplayerOwner;
    }
}
