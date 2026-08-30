package com.moepus.serverwarashi.client.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPerfShaderResourceTest {
    private static final String ROOT = "/assets/serverwarashi/shaders/core/";

    @Test
    void shaderJsonDeclaresCompleteUniformDefaults() throws Exception {
        JsonObject object = json("world_perf_object.json");
        JsonObject beam = json("world_perf_beam.json");
        assertCompleteDefaults(object);
        assertCompleteDefaults(beam);
        assertFalse(object.has("blend"));
        assertFalse(beam.has("blend"));
    }

    @Test
    void objectShaderUsesJavaOwnedContractUniforms() throws Exception {
        JsonObject root = json("world_perf_object.json");
        assertUniform(root, "FadeDistances", WorldOverlayMath.OBJECT_FADE_START,
                WorldOverlayMath.OBJECT_FADE_END);
        assertUniform(root, "LabelDistances", WorldOverlayMath.LABEL_FIXED_DISTANCE,
                WorldOverlayMath.LABEL_TRANSITION_END);
        assertUniform(root, "LabelFadeDistances", WorldOverlayMath.LABEL_FADE_START,
                WorldOverlayMath.LABEL_FADE_END);
        assertUniform(root, "OverflowValue", WorldOverlayMath.OVERFLOW_MICROS);
        assertUniform(root, "LabelWordBase", WorldOverlayMath.LABEL_WORD_BASE);
        assertUniform(root, "GlyphCount", WorldGlyphAtlas.GLYPH_COUNT);
        assertUniform(root, "StrengthWordScale", WorldOverlayMath.FLAME_STRENGTH_SCALE);
        assertUniform(root, "ParticlePhaseBase", WorldFlameParticleLayout.PHASE_BASE);
        assertUniform(root, "MaxFlameStrength", WorldOverlayMath.MAX_PACKED_FLAME_STRENGTH);
        assertUniform(root, "MaxFlameViewportFraction",
                WorldOverlayMath.MAX_FLAME_VIEWPORT_FRACTION);
        assertUniform(root, "ParticleTime", 0.0D);
        assertUniform(root, "ParticleEpoch", 0.0D);
        assertUniform(root, "RenderPass", 0.0D);
        assertUniform(root, "ParticleStart", 0.0D);
        assertUniform(root, "SlotsPerMarker", 1.0D);
        assertEquals("Position", root.getAsJsonArray("attributes").get(0).getAsString());
        assertEquals(1, root.getAsJsonArray("attributes").size());
        assertTrue(hasSampler(root, "MarkerData"));
    }

    @Test
    void objectShaderExpandsGpuParticleInstancesFromMarkerData() throws Exception {
        String vertex = resource("world_perf_object.vsh");
        assertTrue(vertex.contains("uniform samplerBuffer MarkerData;"));
        assertTrue(vertex.contains("texelFetch(MarkerData, base)"));
        assertTrue(vertex.contains("gl_InstanceID / SlotsPerMarker"));
        assertTrue(vertex.contains("ParticleStart + slot"));
        assertEquals(WorldFlameParticleLayout.RED_PARTICLE_COUNT,
                shaderIntConstant(vertex, "RED_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.ORANGE_PARTICLE_COUNT,
                shaderIntConstant(vertex, "ORANGE_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.YELLOW_PARTICLE_COUNT,
                shaderIntConstant(vertex, "YELLOW_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.GLOW_PARTICLE_COUNT,
                shaderIntConstant(vertex, "GLOW_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.SPARK_PARTICLE_COUNT,
                shaderIntConstant(vertex, "SPARK_PARTICLE_COUNT"));
        assertTrue(vertex.contains("float slotInterval = cycle / float(layerCapacity(layer));"));
        assertTrue(vertex.contains("ParticleEpoch * cyclesPerParticlePeriod(layer)"));
        assertFalse(vertex.contains("in ivec2 UV2;"));
        assertFalse(vertex.contains("particleIndex * ParticlePhaseBase"));
    }

    @Test
    void objectShaderAppliesLodEnvelopeAndCoherentPlumeSparks() throws Exception {
        String vertex = resource("world_perf_object.vsh");
        String fragment = resource("world_perf_object.fsh");
        assertTrue(vertex.contains("const float PARTICLE_LOD_START = 24.0;"));
        assertTrue(vertex.contains("const float SPARK_FADE_END = 24.0;"));
        assertTrue(vertex.contains("distanceToCamera >= SPARK_FADE_END"));
        assertTrue(vertex.contains("const float MIN_RED_COUNT = 16.0;"));
        assertTrue(vertex.contains("const float MIN_ORANGE_COUNT = 14.0;"));
        assertTrue(vertex.contains("const float MIN_YELLOW_COUNT = 14.0;"));
        assertTrue(vertex.contains("const float MIN_GLOW_COUNT = 10.0;"));
        assertTrue(vertex.contains("const float MIN_SPARK_COUNT = 10.0;"));
        assertTrue(vertex.contains("const vec2 SPARK_COUNT_RAMP = vec2(3.50, 5.0);"));
        assertTrue(vertex.contains("const float GREEN_BASE_WIDTH = 1.5;"));
        assertTrue(vertex.contains("const float GREEN_TIP_WIDTH = 0.8;"));
        assertTrue(vertex.contains("const float PURPLE_BASE_WIDTH = 2.4;"));
        assertTrue(vertex.contains("const float PURPLE_TIP_WIDTH = 1.0;"));
        assertTrue(vertex.contains("float flameWidthEnvelope("));
        assertTrue(vertex.contains("float widthEnvelope = spark ? 1.0"));
        assertTrue(vertex.contains("const vec2 SPARK_ASPECT = vec2(1.0, 1.0);"));
        assertTrue(vertex.contains("const float SPARK_CYCLE = 1.40;"));
        assertTrue(vertex.contains("const float SPARK_LIFETIME = 0.90;"));
        assertTrue(vertex.contains("const int SPARK_BURST_COUNT = 4;"));
        assertTrue(vertex.contains("const int SPARKS_PER_BURST = 16;"));
        assertTrue(vertex.contains("const int SPARK_CYCLES_PER_PERIOD = 5;"));
        assertEquals(WorldFlameParticleLayout.SPARK_PARTICLE_COUNT,
                shaderIntConstant(vertex, "SPARK_BURST_COUNT")
                        * shaderIntConstant(vertex, "SPARKS_PER_BURST"));
        assertTrue(vertex.contains("float sparkPlumeCenter("));
        assertTrue(vertex.contains("float sparkSlotOffset("));
        assertTrue(vertex.contains("SPARK_PLUME_TEMPORAL_HARMONICS"));
        assertTrue(vertex.contains("SPARK_PLUME_WEIGHTS"));
        assertTrue(vertex.contains("SPARK_EMITTER_HALF_WIDTH_RANGE"));
        assertTrue(vertex.contains("SPARK_PLUME_SPREAD_RANGE"));
        assertTrue(vertex.contains("SPARK_SPREAD_LIFE_EXPONENT"));
        assertTrue(vertex.contains("offset = sparkOffset("));
        assertFalse(vertex.contains("brownianMotion"));
        assertFalse(vertex.contains("SPARK_BROWNIAN"));
        assertTrue(fragment.contains("void renderSparkParticle()"));
        assertTrue(fragment.contains("if (particleLayer == 4)"));
        assertTrue(fragment.contains("const float SPARK_CORE_RADIUS = 0.20;"));
        assertTrue(fragment.contains("const float SPARK_HALO_ENERGY = 0.03;"));
        assertTrue(fragment.contains("const float SPARK_WHITE_MIX = 0.72;"));
        assertTrue(fragment.contains("fragColor = vec4(sparkColor * energy, 0.0)"));
    }

    @Test
    void objectFragmentKeepsSmokeAndSdfGlowContracts() throws Exception {
        String fragment = resource("world_perf_object.fsh");
        assertTrue(fragment.contains("struct ParticleShape"));
        assertTrue(fragment.contains("ParticleShape particleShape("));
        assertTrue(fragment.contains("void renderGlowParticle("));
        assertTrue(fragment.contains("void renderSmokeParticle("));
        assertTrue(fragment.contains("vec3 graySmoke = vec3(0.285, 0.290, 0.295);"));
        assertTrue(fragment.contains("fragColor = vec4(smokeColor * alpha, alpha)"));
        assertTrue(fragment.contains("const float CORE_GLOW_ENERGY = 0.09;"));
        assertTrue(fragment.contains("exp2(-NEAR_GLOW_FALLOFF * outsideDistance)"));
        assertTrue(fragment.contains("exp2(-FAR_GLOW_FALLOFF * outsideDistance)"));
        assertTrue(fragment.contains("smoothstep(0.42, 0.50, quadRadius)"));
        assertFalse(fragment.contains("radialHalo"));
        assertFalse(fragment.contains("float severity()"));
    }

    @Test
    void objectGlowDoesNotDeclareAPostProcess() {
        assertNull(WorldPerfShaderResourceTest.class.getResource(
                "/assets/serverwarashi/shaders/post/world_perf_bloom.json"));
    }

    @Test
    void beamShaderUsesJavaOwnedFadeDistances() throws Exception {
        JsonObject root = json("world_perf_beam.json");
        assertUniform(root, "FadeDistances", WorldOverlayMath.BEAM_FADE_START,
                WorldOverlayMath.BEAM_FADE_END);
        assertUniform(root, "CameraOffset", 0.0D, 0.0D);
        String vertex = resource("world_perf_beam.vsh");
        assertTrue(vertex.contains("uniform vec2 FadeDistances;"));
        assertTrue(vertex.contains("length(Position.xz - CameraOffset)"));
        assertFalse(vertex.contains("length(viewPosition.xz)"));
        assertFalse(vertex.contains("smoothRange(64.0, 128.0"));
    }

    private static boolean hasSampler(JsonObject root, String name) {
        for (var sampler : root.getAsJsonArray("samplers")) {
            if (name.equals(sampler.getAsJsonObject().get("name").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static int shaderIntConstant(String shader, String name) {
        String prefix = "const int " + name + " = ";
        int valueStart = shader.indexOf(prefix);
        if (valueStart < 0) {
            throw new AssertionError("Missing shader constant " + name);
        }
        valueStart += prefix.length();
        int valueEnd = shader.indexOf(';', valueStart);
        return Integer.parseInt(shader.substring(valueStart, valueEnd));
    }

    private static void assertCompleteDefaults(JsonObject root) {
        for (var uniform : root.getAsJsonArray("uniforms")) {
            JsonObject object = uniform.getAsJsonObject();
            assertEquals(object.get("count").getAsInt(), object.getAsJsonArray("values").size(),
                    object.get("name").getAsString());
        }
    }

    private static void assertUniform(JsonObject root, String name, double... expected) {
        for (var uniform : root.getAsJsonArray("uniforms")) {
            JsonObject object = uniform.getAsJsonObject();
            if (!name.equals(object.get("name").getAsString())) {
                continue;
            }
            assertEquals(expected.length, object.get("count").getAsInt(), name);
            for (int index = 0; index < expected.length; index++) {
                assertEquals(expected[index], object.getAsJsonArray("values").get(index).getAsDouble(),
                        0.0D, name + "[" + index + "]");
            }
            return;
        }
        throw new AssertionError("Missing uniform " + name);
    }

    private static JsonObject json(String name) throws Exception {
        var stream = WorldPerfShaderResourceTest.class.getResourceAsStream(ROOT + name);
        assertNotNull(stream);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String resource(String name) throws Exception {
        var stream = WorldPerfShaderResourceTest.class.getResourceAsStream(ROOT + name);
        assertNotNull(stream);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
