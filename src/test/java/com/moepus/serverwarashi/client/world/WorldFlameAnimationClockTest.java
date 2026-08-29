package com.moepus.serverwarashi.client.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorldFlameAnimationClockTest {
    @Test
    void sevenSecondWrapIsContinuousForEveryShaderParticleCycle() throws IOException {
        String shader = shader();
        float defaultCycle = shaderFloat(shader, "DEFAULT_PARTICLE_CYCLE");
        float fastSpeed = shaderFloat(shader, "FAST_PARTICLE_SPEED");
        Cycle red = new Cycle(1.0F, defaultCycle,
                shaderInt(shader, "RED_CYCLES_PER_PERIOD"));
        Cycle orange = new Cycle(1.0F, shaderFloat(shader, "ORANGE_PARTICLE_CYCLE"),
                shaderInt(shader, "ORANGE_CYCLES_PER_PERIOD"));
        Cycle fast = new Cycle(fastSpeed, defaultCycle,
                shaderInt(shader, "FAST_CYCLES_PER_PERIOD"));
        Cycle spark = new Cycle(1.0F, shaderFloat(shader, "SPARK_CYCLE"),
                shaderInt(shader, "SPARK_CYCLES_PER_PERIOD"));
        WorldFlameAnimationClock.Snapshot before = WorldFlameAnimationClock.sample(139L, 1.0F);
        WorldFlameAnimationClock.Snapshot after = WorldFlameAnimationClock.sample(140L, 0.0F);

        assertEquals(shaderFloat(shader, "PARTICLE_CLOCK_PERIOD") * 20.0F,
                WorldFlameAnimationClock.PARTICLE_PERIOD_TICKS);
        assertEquals(7.0F, before.particleSeconds());
        assertEquals(0.0F, after.particleSeconds());
        assertEquals(0, before.particleEpoch());
        assertEquals(1, after.particleEpoch());
        assertEquals(generation(before, red), generation(after, red));
        assertEquals(generation(before, orange), generation(after, orange));
        assertEquals(generation(before, fast), generation(after, fast));
        assertEquals(generation(before, spark), generation(after, spark));
    }

    @Test
    void keepsNoiseOnItsIndependentTwentyMinuteClock() {
        assertEquals(1_199.95F,
                WorldFlameAnimationClock.sample(23_999L, 0.0F).noiseSeconds(), 0.001F);
        assertEquals(0.0F,
                WorldFlameAnimationClock.sample(24_000L, 0.0F).noiseSeconds());
    }

    private static int generation(WorldFlameAnimationClock.Snapshot clock, Cycle cycle) {
        return (int) Math.floor(clock.particleSeconds() * cycle.speed() / cycle.duration())
                + clock.particleEpoch() * cycle.cyclesPerPeriod();
    }

    private static String shader() throws IOException {
        String path = "assets/serverwarashi/shaders/core/world_perf_object.vsh";
        try (var stream = WorldFlameAnimationClockTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float shaderFloat(String shader, String name) {
        return Float.parseFloat(shaderConstant(shader, "float", name));
    }

    private static int shaderInt(String shader, String name) {
        return Integer.parseInt(shaderConstant(shader, "int", name));
    }

    private static String shaderConstant(String shader, String type, String name) {
        var pattern = Pattern.compile("const\\s+" + type + "\\s+" + name
                + "\\s*=\\s*([0-9.]+)\\s*;");
        var matcher = pattern.matcher(shader);
        if (!matcher.find()) {
            throw new AssertionError("Missing shader constant " + name);
        }
        return matcher.group(1);
    }

    private record Cycle(float speed, float duration, int cyclesPerPeriod) {
    }
}
