package com.moepus.serverwarashi.client.screen;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnknownStackLocalizationTest {
    @Test
    void chineseInitialTooltipMatchesRequestedText() throws Exception {
        var translations = translations("zh_cn");
        assertEquals("点击复制详情", translations.get(
                "screen.serverwarashi.profiler.unknown.click").getAsString());
    }

    @Test
    void bothLocalesContainEveryRequestStateAndCopyFeedback() throws Exception {
        List<String> keys = List.of("click", "fetching", "copied", "timeout",
                "stale", "empty", "denied", "busy", "error");
        for (String locale : List.of("zh_cn", "en_us")) {
            var translations = translations(locale);
            for (String key : keys) {
                assertNotNull(translations.get(
                        "screen.serverwarashi.profiler.unknown." + key));
            }
            assertNotNull(translations.get("message.serverwarashi.unknown.copied"));
        }
    }

    private com.google.gson.JsonObject translations(String locale) throws Exception {
        var stream = getClass().getResourceAsStream(
                "/assets/serverwarashi/lang/" + locale + ".json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
