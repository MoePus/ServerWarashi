package com.moepus.serverwarashi.client.world;

final class WorldFlameParticleLayout {
    static final int OUTER_PARTICLE_COUNT = 24;
    static final int BODY_PARTICLE_COUNT = 20;
    static final int CORE_PARTICLE_COUNT = 20;
    static final int SPARK_PARTICLE_COUNT = 12;

    static final int BODY_PARTICLE_START = OUTER_PARTICLE_COUNT;
    static final int CORE_PARTICLE_START = BODY_PARTICLE_START + BODY_PARTICLE_COUNT;
    static final int SPARK_PARTICLE_START = CORE_PARTICLE_START + CORE_PARTICLE_COUNT;
    static final int FLAME_PARTICLE_COUNT = SPARK_PARTICLE_START;

    static final int PHASE_BASE = 512;
    private static final int PHASE_MASK = PHASE_BASE - 1;

    private WorldFlameParticleLayout() {
    }

    static int phaseWord(int phase) {
        return phase & PHASE_MASK;
    }
}
