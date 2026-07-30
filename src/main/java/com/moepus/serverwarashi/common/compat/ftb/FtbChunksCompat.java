package com.moepus.serverwarashi.common.compat.ftb;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.LoadingModList;

import java.util.Optional;
import java.util.UUID;

public final class FtbChunksCompat {
    private static final ResourceLocation TICKET_CONTROLLER = FTBChunksAPI.rl("default");

    private FtbChunksCompat() {
    }

    public static Optional<FtbTeamAttribution.Result> resolve(Object ticketId, Object ticketOwner) {
        if (!TICKET_CONTROLLER.equals(ticketId) || !(ticketOwner instanceof UUID teamId)) {
            return Optional.empty();
        }
        FtbTeamAttribution.Result attribution = isFtbTeamsLoaded()
                ? FtbTeamsCompat.resolve(teamId)
                : FtbTeamAttribution.resolve(teamId, null);
        return Optional.of(attribution);
    }

    private static boolean isFtbTeamsLoaded() {
        LoadingModList modList = LoadingModList.get();
        return modList != null && modList.getModFileById("ftbteams") != null;
    }
}
