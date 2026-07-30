package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.compat.ftb.FtbChunksCompat;
import com.moepus.serverwarashi.common.compat.ftb.FtbTeamAttribution;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.neoforged.fml.loading.LoadingModList;

import java.util.Optional;

final class FtbForcedChunkAttribution {
    private FtbForcedChunkAttribution() {
    }

    static Optional<PerfAttributionSnapshot.Bucket> resolve(
            ServerLevel level, TicketOwnerResolver.ForcedOwner forced, Ticket<?> ticket) {
        if (!isLoaded()) {
            return Optional.empty();
        }
        Context context = new Context(level, forced, ticket);
        return FtbChunksCompat.resolve(forced.id(), forced.owner())
                .map(attribution -> bucket(context, attribution));
    }

    private static PerfAttributionSnapshot.Bucket bucket(
            Context context,
            FtbTeamAttribution.Result attribution) {
        PerfAttributionSnapshot.Owner owner = attribution.player()
                ? PlayerAttribution.known(context.level.getServer(), attribution.playerId())
                : new PerfAttributionSnapshot.Owner(PerfReportData.AttributionKind.LOADER,
                attribution.ownerId(), attribution.ownerName(), false, 0L, null);
        String sourceId = context.level.dimension().location() + ":"
                + context.forced.id() + ":" + context.forced.owner();
        var source = new PerfAttributionSnapshot.Source(sourceId, attribution.sourceName(), null);
        return new PerfAttributionSnapshot.Bucket(owner, source, 0, 0,
                LoaderTicketInfluence.blockTickingRadius(context.ticket));
    }

    private static boolean isLoaded() {
        LoadingModList modList = LoadingModList.get();
        return modList != null
                && modList.getModFileById("ftbchunks") != null;
    }

    private record Context(ServerLevel level,
                           TicketOwnerResolver.ForcedOwner forced,
                           Ticket<?> ticket) {
    }
}
