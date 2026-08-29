package com.moepus.serverwarashi.client.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPerfBeamRendererTest {
    @Test
    void preservesCoreWidthAtTheWorldBorder() {
        double center = 29_999_992.0D;
        double origin = WorldPerfBeamRenderer.localOrigin(center);
        float left = WorldPerfBeamRenderer.localCoordinate(
                center - WorldPerfBeamRenderer.CORE_HALF_WIDTH, origin);
        float right = WorldPerfBeamRenderer.localCoordinate(
                center + WorldPerfBeamRenderer.CORE_HALF_WIDTH, origin);

        assertNotEquals(left, right);
        assertEquals(WorldPerfBeamRenderer.CORE_HALF_WIDTH * 2.0D,
                right - left, 0.001D);
        assertTrue(Math.abs(center - origin) < 4_096.0D);
    }

    @Test
    void usesFloorAlignedOriginsForNegativeCoordinates() {
        assertEquals(-4_096.0D, WorldPerfBeamRenderer.localOrigin(-1.0D));
        assertEquals(-8_192.0D, WorldPerfBeamRenderer.localOrigin(-4_097.0D));
    }
}
