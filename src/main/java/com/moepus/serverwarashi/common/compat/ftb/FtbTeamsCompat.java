package com.moepus.serverwarashi.common.compat.ftb;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;

import java.util.Optional;
import java.util.UUID;

final class FtbTeamsCompat {
    private FtbTeamsCompat() {
    }

    static FtbTeamAttribution.Result resolve(UUID teamId) {
        FtbTeamAttribution.TeamView team = findTeam(teamId)
                .map(FtbTeamsCompat::teamView)
                .orElse(null);
        return FtbTeamAttribution.resolve(teamId, team);
    }

    private static Optional<Team> findTeam(UUID teamId) {
        var api = FTBTeamsAPI.api();
        return api.isManagerLoaded()
                ? api.getManager().getTeamByID(teamId)
                : Optional.empty();
    }

    private static FtbTeamAttribution.TeamView teamView(Team team) {
        return new FtbTeamAttribution.TeamView(
                team.getOwner(), team.getName().getString(), team.isPlayerTeam());
    }
}
