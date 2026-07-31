package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BlockLoaderTagResourceTest {
    @Test
    void defaultTagContainsOnlyWorkingCreatePowerLoaders() throws Exception {
        var stream = getClass().getClassLoader()
                .getResourceAsStream("data/serverwarashi/tags/block/chunk_loaders.json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var values = root.getAsJsonArray("values");
            assertEquals(2, values.size());
            assertEquals("create_power_loader:andesite_chunk_loader",
                    values.get(0).getAsJsonObject().get("id").getAsString());
            assertEquals("create_power_loader:brass_chunk_loader",
                    values.get(1).getAsJsonObject().get("id").getAsString());
            values.forEach(value -> assertFalse(value.getAsJsonObject().get("required").getAsBoolean()));
        }
    }
}
