package com.moepus.serverwarashi.common.compat.ftb;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtbTeamAttributionTest {
    private static final UUID TEAM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void mapsPlayerTeamToItsOwnerPlayer() {
        var result = FtbTeamAttribution.resolve(TEAM_ID,
                new FtbTeamAttribution.TeamView(PLAYER_ID, "Alice", true));

        assertTrue(result.player());
        assertEquals(PLAYER_ID, result.playerId());
        assertEquals("FTB Chunks", result.sourceName());
    }

    @Test
    void mapsPartyAndServerTeamsToNamedLoaders() {
        for (String name : new String[]{"Builders", "Spawn Team"}) {
            var result = FtbTeamAttribution.resolve(TEAM_ID,
                    new FtbTeamAttribution.TeamView(PLAYER_ID, name, false));

            assertFalse(result.player());
            assertEquals("ftbchunks:team:" + TEAM_ID, result.ownerId());
            assertEquals("FTB Chunks / " + name, result.ownerName());
            assertEquals(result.ownerName(), result.sourceName());
        }
    }

    @Test
    void usesTeamIdWhenTeamIsNotAvailable() {
        var result = FtbTeamAttribution.resolve(TEAM_ID, null);

        assertFalse(result.player());
        assertEquals("ftbchunks:team:" + TEAM_ID, result.ownerId());
        assertEquals("FTB Chunks / " + TEAM_ID, result.ownerName());
    }
}
