package com.moepus.serverwarashi.client.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldFlameParticleLayoutTest {
    @Test
    void reservesSixtyFourFlameSlotsFollowedByTwelveSparks() {
        assertEquals(24, WorldFlameParticleLayout.OUTER_PARTICLE_COUNT);
        assertEquals(20, WorldFlameParticleLayout.BODY_PARTICLE_COUNT);
        assertEquals(20, WorldFlameParticleLayout.CORE_PARTICLE_COUNT);
        assertEquals(12, WorldFlameParticleLayout.SPARK_PARTICLE_COUNT);
        assertEquals(24, WorldFlameParticleLayout.BODY_PARTICLE_START);
        assertEquals(44, WorldFlameParticleLayout.CORE_PARTICLE_START);
        assertEquals(64, WorldFlameParticleLayout.SPARK_PARTICLE_START);
        assertEquals(64, WorldFlameParticleLayout.FLAME_PARTICLE_COUNT);
    }

    @Test
    void preservesTheExistingPhaseRangeWithoutParticlePacking() {
        assertEquals(512, WorldFlameParticleLayout.PHASE_BASE);
        assertEquals(511, WorldFlameParticleLayout.phaseWord(0x7FFF));
        assertEquals(0, WorldFlameParticleLayout.phaseWord(512));
    }
}
