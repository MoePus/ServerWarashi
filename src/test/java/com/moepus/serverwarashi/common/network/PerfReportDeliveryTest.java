package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfReportDeliveryTest {
    @Test
    void waitsForANewPublishedSamplerSnapshot() {
        assertFalse(PerfReportDelivery.hasNewSnapshot(null, -1L));
        assertFalse(PerfReportDelivery.hasNewSnapshot(PerfReportData.empty(1_000), -1L));

        PerfReportData tick20 = reportAtTick(20L);
        assertTrue(PerfReportDelivery.hasNewSnapshot(tick20, -1L));
        assertFalse(PerfReportDelivery.hasNewSnapshot(tick20, 20L));
        assertTrue(PerfReportDelivery.hasNewSnapshot(reportAtTick(40L), 20L));
    }

    @Test
    void requiresChannelAndOperatorPermissionForMapSubscription() {
        assertTrue(PerfReportDelivery.canSubscribeMap(true, true));
        assertFalse(PerfReportDelivery.canSubscribeMap(false, true));
        assertFalse(PerfReportDelivery.canSubscribeMap(true, false));
    }

    @Test
    void keepsScreenAndMapSubscriptionsIndependent() {
        UUID screen = UUID.randomUUID();
        UUID map = UUID.randomUUID();
        UUID both = UUID.randomUUID();
        var subscriptions = new PerfReportDelivery.ViewerSubscriptions();
        subscriptions.openScreen(screen);
        subscriptions.openScreen(both);
        subscriptions.setMap(map, true);
        subscriptions.setMap(both, true);

        assertEquals(Set.of(screen, map, both), subscriptions.recipients());
        assertTrue(subscriptions.isLiveViewer(screen));
        assertTrue(subscriptions.isLiveViewer(map));
        assertTrue(subscriptions.isLiveViewer(both));
        subscriptions.setMap(both, false);
        assertTrue(subscriptions.isScreenViewer(both));
        assertFalse(subscriptions.isMapViewer(both));
        assertEquals(Set.of(screen, map, both), subscriptions.recipients());

        subscriptions.remove(map);
        assertFalse(subscriptions.isLiveViewer(map));
        assertEquals(Set.of(screen, both), subscriptions.recipients());
    }

    private static PerfReportData reportAtTick(long tickCount) {
        return new PerfReportData(10.0D, 1_000L, 123_000L, tickCount, 10L, 1_000,
                0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
