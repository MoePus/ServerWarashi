package com.moepus.serverwarashi.client.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldFlameParticleLayoutTest {
    @Test
    void reservesOneHundredFortyFourSlotsWithSparksAsTheLargestLayer() {
        assertEquals(24, WorldFlameParticleLayout.RED_PARTICLE_COUNT);
        assertEquals(20, WorldFlameParticleLayout.ORANGE_PARTICLE_COUNT);
        assertEquals(20, WorldFlameParticleLayout.YELLOW_PARTICLE_COUNT);
        assertEquals(16, WorldFlameParticleLayout.GLOW_PARTICLE_COUNT);
        assertEquals(64, WorldFlameParticleLayout.SPARK_PARTICLE_COUNT);
        assertEquals(24, WorldFlameParticleLayout.ORANGE_PARTICLE_START);
        assertEquals(44, WorldFlameParticleLayout.YELLOW_PARTICLE_START);
        assertEquals(64, WorldFlameParticleLayout.GLOW_PARTICLE_START);
        assertEquals(80, WorldFlameParticleLayout.SPARK_PARTICLE_START);
        assertEquals(64, WorldFlameParticleLayout.NORMAL_PARTICLE_COUNT);
        assertEquals(80, WorldFlameParticleLayout.FIRE_PARTICLE_COUNT);
        assertEquals(144, WorldFlameParticleLayout.PARTICLE_COUNT);
    }

    @Test
    void preservesTheExistingPhaseRangeWithoutParticlePacking() {
        assertEquals(512, WorldFlameParticleLayout.PHASE_BASE);
        assertEquals(511, WorldFlameParticleLayout.phaseWord(0x7FFF));
        assertEquals(0, WorldFlameParticleLayout.phaseWord(512));
    }
}
