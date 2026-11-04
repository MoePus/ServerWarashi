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
        assertUniform(root, "PartialTick", 0.0D);
        assertUniform(root, "ParticleTime", 0.0D);
        assertUniform(root, "ParticleEpoch", 0.0D);
        assertUniform(root, "RenderPass", 0.0D);
        assertUniform(root, "ParticleStart", 0.0D);
        assertUniform(root, "SlotsPerMarker", 1.0D);
        assertUniform(root, "MarkerBase", 0.0D);
        assertEquals("Position", root.getAsJsonArray("attributes").get(0).getAsString());
        assertEquals(1, root.getAsJsonArray("attributes").size());
        assertTrue(hasSampler(root, "MarkerData"));
        assertTrue(hasSampler(root, "Sampler0"));
        assertFalse(hasSampler(root, "Sampler1"));
        assertFalse(hasUniform(root, "AnimationTime"));
    }

    @Test
    void objectShaderExpandsGpuParticleInstancesFromMarkerData() throws Exception {
        String vertex = resource("world_perf_object.vsh");
        assertTrue(vertex.contains("uniform samplerBuffer MarkerData;"));
        assertTrue(vertex.contains("texelFetch(MarkerData, base)"));
        assertTrue(vertex.contains("texelFetch(MarkerData, base + 3)"));
        assertEquals(4, shaderIntConstant(vertex, "MARKER_TEXELS"));
        assertTrue(vertex.contains("gl_InstanceID / SlotsPerMarker"));
        assertTrue(vertex.contains("MarkerBase + batchMarkerIndex"));
        assertTrue(vertex.contains("gl_InstanceID - batchMarkerIndex * SlotsPerMarker"));
        assertTrue(vertex.contains("ParticleStart + slot"));
        assertTrue(vertex.contains("mix(marker.previousPosition, marker.position"));
        assertTrue(vertex.contains("clamp(PartialTick, 0.0, 1.0)"));
        assertEquals(WorldFlameParticleLayout.OUTER_PARTICLE_COUNT,
                shaderIntConstant(vertex, "OUTER_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.BODY_PARTICLE_COUNT,
                shaderIntConstant(vertex, "BODY_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.CORE_PARTICLE_COUNT,
                shaderIntConstant(vertex, "CORE_PARTICLE_COUNT"));
        assertEquals(WorldFlameParticleLayout.SPARK_PARTICLE_COUNT,
                shaderIntConstant(vertex, "SPARK_PARTICLE_COUNT"));
        assertEquals(WorldPerfObjectBuffers.Batch.FLAME.renderPass(),
                shaderIntConstant(vertex, "FLAME_PASS"));
        assertEquals(WorldPerfObjectBuffers.Batch.SPARK.renderPass(),
                shaderIntConstant(vertex, "SPARK_PASS"));
        assertEquals(WorldPerfObjectBuffers.Batch.LABEL.renderPass(),
                shaderIntConstant(vertex, "LABEL_PASS"));
        assertTrue(vertex.contains("int(window) + ParticleEpoch * cyclesPerPeriod"));
        assertFalse(vertex.contains("in ivec2 UV2;"));
        assertFalse(vertex.contains("particleIndex * ParticlePhaseBase"));
    }

    @Test
    void objectShaderDrawsGridSnappedPixelSquaresWithDistanceLod() throws Exception {
        String vertex = resource("world_perf_object.vsh");
        assertEquals(16.0D, shaderFloatConstant(vertex, "PARTICLE_LOD_START"));
        assertEquals(32.0D, shaderFloatConstant(vertex, "PARTICLE_LOD_END"));
        assertEquals(WorldOverlayMath.SPARK_FADE_START,
                shaderFloatConstant(vertex, "SPARK_FADE_START"));
        assertEquals(WorldOverlayMath.SPARK_FADE_END,
                shaderFloatConstant(vertex, "SPARK_FADE_END"));
        assertEquals(16.0D, shaderFloatConstant(vertex, "MIN_OUTER_COUNT"));
        assertEquals(14.0D, shaderFloatConstant(vertex, "MIN_BODY_COUNT"));
        assertEquals(14.0D, shaderFloatConstant(vertex, "MIN_CORE_COUNT"));
        assertEquals(3.0D, shaderFloatConstant(vertex, "MIN_SPARK_COUNT"));
        assertEquals(4.0D, shaderFloatConstant(vertex, "FAR_OUTER_COUNT"));
        assertEquals(3.0D, shaderFloatConstant(vertex, "FAR_BODY_COUNT"));
        assertEquals(3.0D, shaderFloatConstant(vertex, "FAR_CORE_COUNT"));
        assertTrue(vertex.contains("float fastLod = 1.0 - (1.0 - lodAmount)"));
        assertTrue(vertex.contains("nearCount * sparkRemaining * sparkRemaining"));
        // LOD culling happens before any per-particle hashing or motion.
        assertTrue(vertex.indexOf("if (!selectedParticleSlot(")
                < vertex.indexOf("if (!emitParticle("));
        // One flame pixel at the lowest severity is one block texel.
        assertTrue(vertex.contains("const vec2 PIXEL_WORLD_SIZE = vec2(0.0625, "));
        assertEquals(1.0D, shaderFloatConstant(vertex, "PIXEL_SNAP"));
        // Squares scale equally on both axes, so they are never stretched.
        assertTrue(vertex.contains("(center + (localCoord - 0.5) * particle.pixels) * scale"));
        assertTrue(vertex.contains("vec2 corner = floor(center - pixels * 0.5 + 0.5);"));
        assertTrue(vertex.contains("if (temperature < BURNOUT_TEMPERATURE)"));
    }

    @Test
    void objectFragmentDrawsFlatPremultipliedSquares() throws Exception {
        String fragment = resource("world_perf_object.fsh");
        assertTrue(fragment.contains("void renderParticle()"));
        assertTrue(fragment.contains("const float CHIP_CORNERS_FROM = 4.0;"));
        assertTrue(fragment.contains("fragColor = vec4(vertexColor.rgb * alpha, alpha);"));
        assertFalse(fragment.contains("Sampler1"));
        assertFalse(fragment.contains("AnimationTime"));
        assertFalse(fragment.contains("renderGlowParticle"));
        assertFalse(fragment.contains("renderSmokeParticle"));
    }

    @Test
    void objectOverlayDoesNotDeclareAPostProcess() {
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

    private static boolean hasUniform(JsonObject root, String name) {
        for (var uniform : root.getAsJsonArray("uniforms")) {
            if (name.equals(uniform.getAsJsonObject().get("name").getAsString())) {
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

    private static double shaderFloatConstant(String shader, String name) {
        String prefix = "const float " + name + " = ";
        int valueStart = shader.indexOf(prefix);
        if (valueStart < 0) {
            throw new AssertionError("Missing shader constant " + name);
        }
        valueStart += prefix.length();
        int valueEnd = shader.indexOf(';', valueStart);
        return Double.parseDouble(shader.substring(valueStart, valueEnd));
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
