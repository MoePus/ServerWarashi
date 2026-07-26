package com.moepus.serverwarashi.client.heatmap;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
