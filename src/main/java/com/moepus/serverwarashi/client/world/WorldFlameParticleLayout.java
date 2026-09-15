package com.moepus.serverwarashi.client.world;

final class WorldFlameParticleLayout {
    static final int RED_PARTICLE_COUNT = 24;
    static final int ORANGE_PARTICLE_COUNT = 20;
    static final int YELLOW_PARTICLE_COUNT = 20;
    static final int GLOW_PARTICLE_COUNT = 16;
    static final int SPARK_PARTICLE_COUNT = 64;

    static final int ORANGE_PARTICLE_START = RED_PARTICLE_COUNT;
    static final int YELLOW_PARTICLE_START = ORANGE_PARTICLE_START + ORANGE_PARTICLE_COUNT;
    static final int GLOW_PARTICLE_START = YELLOW_PARTICLE_START + YELLOW_PARTICLE_COUNT;
    static final int SPARK_PARTICLE_START = GLOW_PARTICLE_START + GLOW_PARTICLE_COUNT;
    static final int NORMAL_PARTICLE_COUNT = GLOW_PARTICLE_START;

    static final int PHASE_BASE = 512;
    private static final int PHASE_MASK = PHASE_BASE - 1;

    private WorldFlameParticleLayout() {
    }

    static int phaseWord(int phase) {
        return phase & PHASE_MASK;
    }
}
