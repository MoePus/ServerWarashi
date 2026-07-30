package com.moepus.serverwarashi.common.core.owner;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class C2meNoTickAttributionTest {
    @Test
    void recognizesOnlyTheExactTicketTypeName() {
        assertTrue(C2meNoTickAttribution.isTicketType("c2me_notickvd"));
        assertFalse(C2meNoTickAttribution.isTicketType("c2me:notickvd"));
        assertFalse(C2meNoTickAttribution.isTicketType("c2me_notickvd_extra"));
    }

    @Test
    void addsC2meViewDistancePadding() {
        assertEquals(13, C2meNoTickAttribution.radius(12));
    }

    @Test
    void usesAnInclusiveSquareCoverageArea() {
        ChunkPos source = new ChunkPos(10, -10);

        assertTrue(C2meNoTickAttribution.covers(source, new ChunkPos(23, 3), 13));
        assertTrue(C2meNoTickAttribution.covers(source, new ChunkPos(-3, -23), 13));
        assertFalse(C2meNoTickAttribution.covers(source, new ChunkPos(24, 3), 13));
        assertFalse(C2meNoTickAttribution.covers(source, new ChunkPos(23, 4), 13));
    }
}
