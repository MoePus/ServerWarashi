package com.moepus.serverwarashi.client.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldFlameAnimationClockTest {
    @Test
    void sevenSecondWrapIsContinuousForEveryShaderEmissionCadence() throws IOException {
        String shader = shader();
        WorldFlameAnimationClock.Snapshot before = WorldFlameAnimationClock.sample(139L, 1.0F);
        WorldFlameAnimationClock.Snapshot after = WorldFlameAnimationClock.sample(140L, 0.0F);

        assertEquals(shaderFloat(shader, "PARTICLE_CLOCK_PERIOD") * 20.0F,
                WorldFlameAnimationClock.PARTICLE_PERIOD_TICKS);
        assertEquals(7.0F, before.particleSeconds());
        assertEquals(0.0F, after.particleSeconds());
        assertEquals(0, before.particleEpoch());
        assertEquals(1, after.particleEpoch());
        // emitParticle derives every cycle from the period, so any whole cycle count wraps cleanly.
        assertTrue(shader.contains(
                "float cycle = PARTICLE_CLOCK_PERIOD / float(cyclesPerPeriod);"));
        assertTrue(shader.contains("int(window) + ParticleEpoch * cyclesPerPeriod"));
        int sparkMinimum = shaderInt(shader, "SPARK_MIN_CYCLES_PER_PERIOD");
        int sparkVariants = shaderInt(shader, "SPARK_CYCLE_VARIANTS");
        int[] cadences = {
                shaderInt(shader, "OUTER_CYCLES_PER_PERIOD"),
                shaderInt(shader, "BODY_CYCLES_PER_PERIOD"),
                shaderInt(shader, "CORE_CYCLES_PER_PERIOD"),
                sparkMinimum,
                sparkMinimum + sparkVariants - 1
        };
        for (int cyclesPerPeriod : cadences) {
            assertEquals(generation(before, cyclesPerPeriod), generation(after, cyclesPerPeriod),
                    "cycles per period " + cyclesPerPeriod);
        }
    }

    private static int generation(WorldFlameAnimationClock.Snapshot clock, int cyclesPerPeriod) {
        float cycle = WorldFlameAnimationClock.PARTICLE_PERIOD_TICKS / 20.0F / cyclesPerPeriod;
        return (int) Math.floor(clock.particleSeconds() / cycle)
                + clock.particleEpoch() * cyclesPerPeriod;
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
}
