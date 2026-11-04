package com.moepus.serverwarashi.client.world;

final class WorldFlameAnimationClock {
    static final long PARTICLE_PERIOD_TICKS = 140L;
    private static final float TICKS_PER_SECOND = 20.0F;

    private WorldFlameAnimationClock() {
    }

    static Snapshot sample(long gameTime, float partialTick) {
        long particleTick = Math.floorMod(gameTime, PARTICLE_PERIOD_TICKS);
        long particleEpoch = Math.floorDiv(gameTime, PARTICLE_PERIOD_TICKS);
        return new Snapshot(
                (particleTick + partialTick) / TICKS_PER_SECOND,
                (int) particleEpoch);
    }

    record Snapshot(float particleSeconds, int particleEpoch) {
    }
}
