package com.moepus.serverwarashi.common.modules.performance.analyze;

import org.junit.jupiter.api.Test;

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
    void onlyClearsCompletedReportsWhileIdle() {
        PerfSessionController controller = new PerfSessionController();

        assertTrue(controller.clearLastReport());
        assertTrue(controller.schedule(10, null));
        assertFalse(controller.clearLastReport());
        assertTrue(controller.cancelPending());
        assertTrue(controller.clearLastReport());
    }
}
