package com.moepus.serverwarashi.client.world;

final class WorldFlameAnimationClock {
    static final long PARTICLE_PERIOD_TICKS = 140L;
    private static final long NOISE_PERIOD_TICKS = 24_000L;
    private static final float TICKS_PER_SECOND = 20.0F;

    private WorldFlameAnimationClock() {
    }

    static Snapshot sample(long gameTime, float partialTick) {
        long particleTick = Math.floorMod(gameTime, PARTICLE_PERIOD_TICKS);
        long particleEpoch = Math.floorDiv(gameTime, PARTICLE_PERIOD_TICKS);
        long noiseTick = Math.floorMod(gameTime, NOISE_PERIOD_TICKS);
        return new Snapshot(
                (noiseTick + partialTick) / TICKS_PER_SECOND,
                (particleTick + partialTick) / TICKS_PER_SECOND,
                (int) particleEpoch);
    }

    record Snapshot(float noiseSeconds, float particleSeconds, int particleEpoch) {
    }
}
