package com.moepus.serverwarashi.client.heatmap;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkHeatmapShaderResourceTest {
    @Test
    void iconShaderDeclaresCompleteUniformDefaults() {
        String path = "/assets/serverwarashi/shaders/core/chunk_loader_icon.json";
        var stream = ChunkHeatmapShaderResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream);
        var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();

        for (var uniform : root.getAsJsonArray("uniforms")) {
            var object = uniform.getAsJsonObject();
            assertEquals(object.get("count").getAsInt(), object.getAsJsonArray("values").size(),
                    object.get("name").getAsString());
        }
    }

    @Test
    void inactiveIconStateChangesColorWithoutChangingGeometry() throws Exception {
        String vertex = resource("/assets/serverwarashi/shaders/core/chunk_loader_icon.vsh");
        String fragment = resource("/assets/serverwarashi/shaders/core/chunk_loader_icon.fsh");

        assertTrue(vertex.contains("localCoord = UV0;"));
        assertFalse(vertex.contains("UV0 * Color.r"));
        assertTrue(vertex.contains("loaderWorking = Color.r;"));
        assertTrue(fragment.contains("loaderWorking < 0.5"));
    }

    private static String resource(String path) throws Exception {
        var stream = ChunkHeatmapShaderResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
