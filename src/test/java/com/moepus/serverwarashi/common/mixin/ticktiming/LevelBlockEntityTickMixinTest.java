package com.moepus.serverwarashi.common.mixin.ticktiming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelBlockEntityTickMixinTest {
    private static final Path MIXIN_SOURCE = Path.of(
            "src/main/java/com/moepus/serverwarashi/common/mixin/ticktiming/LevelBlockEntityTickMixin.java");
    private static final Path TRACKER_SOURCE = Path.of(
            "src/main/java/com/moepus/serverwarashi/common/modules/performance/sampling/PerfWorkTracker.java");
    private static final String TICKER_INVOKE =
            "BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;";

    @Test
    void trackerScopeIsBoundToTheTickerInvocation() throws IOException {
        String source = Files.readString(MIXIN_SOURCE);
        String enterInjection = injectionFor(source, "serverWarashi$beforeBlockEntityTick");
        String exitInjection = injectionFor(source, "serverWarashi$afterBlockEntityTick");

        assertTrue(enterInjection.contains(TICKER_INVOKE));
        assertTrue(exitInjection.contains(TICKER_INVOKE));
        assertTrue(exitInjection.contains("shift = At.Shift.AFTER"));
        assertFalse(source.contains("@At(\"RETURN\")"));
    }

    @Test
    void blockEntityLevelChecksBelongToTheTracker() throws IOException {
        String mixin = Files.readString(MIXIN_SOURCE);
        String tracker = Files.readString(TRACKER_SOURCE);
        int callback = mixin.indexOf("private void serverWarashi$afterBlockEntityTick");
        assertTrue(callback >= 0);
        String exitCallback = mixin.substring(callback);

        assertTrue(exitCallback.contains("PerfWorkTracker.exitBlockEntity(blockEntity)"));
        assertFalse(mixin.contains("instanceof ServerLevel"));
        assertTrue(tracker.contains("public static boolean enterBlockEntity"));
        assertTrue(tracker.contains("public static boolean exitBlockEntity"));
    }

    private static String injectionFor(String source, String callbackName) {
        int callback = source.indexOf("private void " + callbackName);
        assertTrue(callback >= 0, callbackName);
        int annotation = source.lastIndexOf("@InjectLite(", callback);
        assertTrue(annotation >= 0, callbackName);
        return source.substring(annotation, callback);
    }
}
