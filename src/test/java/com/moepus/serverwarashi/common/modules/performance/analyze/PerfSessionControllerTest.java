package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfSessionControllerTest {
    @Test
    void readsNeoForgeTickTimeRingAtCurrentTickIndex() {
        long[] tickTimes = {10L, 20L, 30L, 40L};

        assertEquals(30L, PerfSessionController.ringValue(tickTimes, 6));
        assertEquals(10L, PerfSessionController.ringValue(tickTimes, 8));
    }

    @Test
    void startPublishesAttributionBeforeFirstTickAndBlocksClear() {
        PerfSessionController controller = new PerfSessionController(() -> 1_000);
        var owner = new PerfAttributionSnapshot.Owner(
                PerfReportData.AttributionKind.LOADER, "loader", "Loader", false, 0L, null);
        var source = new PerfAttributionSnapshot.Source("source", "Loader", null);
        var attribution = PerfAttributionSnapshot.create("minecraft:overworld", Map.of(
                ChunkPos.asLong(3, 4), List.of(new PerfAttributionSnapshot.Bucket(owner, source, 0, 0))));

        assertTrue(controller.clearLastReport());
        assertTrue(controller.start(10, null, attribution));
        assertFalse(controller.clearLastReport());
        assertEquals(0L, controller.currentReport().tickCount());
        assertEquals(1, controller.currentReport().loaderSources().size());
        controller.stop();
        assertTrue(controller.clearLastReport());
    }
}
