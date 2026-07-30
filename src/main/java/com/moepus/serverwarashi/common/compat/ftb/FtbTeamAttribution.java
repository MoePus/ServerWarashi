package com.moepus.serverwarashi.common.compat.ftb;

import java.util.UUID;

public final class FtbTeamAttribution {
    private static final String LOADER_PREFIX = "FTB Chunks / ";
    private static final String OWNER_ID_PREFIX = "ftbchunks:team:";
    private static final String SOURCE_NAME = "FTB Chunks";

    private FtbTeamAttribution() {
    }

    static Result resolve(UUID ticketTeamId, TeamView team) {
        if (team != null && team.playerTeam()) {
            return Result.player(team.ownerId());
        }
        String teamName = team == null || team.name().isBlank()
                ? ticketTeamId.toString() : team.name();
        return Result.loader(OWNER_ID_PREFIX + ticketTeamId, LOADER_PREFIX + teamName);
    }

    record TeamView(UUID ownerId, String name, boolean playerTeam) {
    }

    public record Result(UUID playerId, String ownerId, String ownerName, String sourceName) {
        static Result player(UUID playerId) {
            return new Result(playerId, null, null, SOURCE_NAME);
        }

        static Result loader(String ownerId, String name) {
            return new Result(null, ownerId, name, name);
        }

        public boolean player() {
            return playerId != null;
        }
    }
}
