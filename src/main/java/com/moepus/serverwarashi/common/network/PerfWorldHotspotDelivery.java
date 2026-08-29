package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class PerfWorldHotspotDelivery {
    private static final PerfWorldHotspotStream STREAM = new PerfWorldHotspotStream();
    private static final Map<UUID, PerfWorldHotspotStream.Cursor> CURSORS = new HashMap<>();

    private PerfWorldHotspotDelivery() {
    }

    static void send(ServerPlayer player,
                     PerfWorldHotspotIndex hotspots,
                     boolean forceFull,
                     boolean retainCursor) {
        updateStream(player.serverLevel().getServer(), hotspots);
        UUID playerId = player.getUUID();
        PerfWorldHotspotStream.Cursor cursor = forceFull ? null : CURSORS.get(playerId);
        PerfWorldHotspotStream.Emission emission = STREAM.emit(
                cursor, player.serverLevel().dimension().location(), player.getX(), player.getZ(),
                TicketPerfConfig.worldHotspotLimit());
        WarashiNetwork.sendPerfWorldHotspots(player, emission.payload());
        if (retainCursor) {
            CURSORS.put(playerId, emission.cursor());
        } else {
            CURSORS.remove(playerId);
        }
    }

    static void remove(UUID playerId) {
        CURSORS.remove(playerId);
    }

    static void clear() {
        CURSORS.clear();
        STREAM.clear();
    }

    private static void updateStream(
            MinecraftServer server, PerfWorldHotspotIndex hotspots) {
        if (!STREAM.hasRevision(hotspots)) {
            STREAM.update(PerfWorldHotspotResolver.refreshEntityPositions(server, hotspots));
        }
    }
}
