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
    void defersOnlyTheLoaderOnlyReportOfAnActiveSession() {
        assertTrue(PerfReportDelivery.shouldDeferInitialReport(
                PerfReportData.empty(1_000), true, false));
        assertFalse(PerfReportDelivery.shouldDeferInitialReport(
                PerfReportData.empty(1_000), true, true));
        assertFalse(PerfReportDelivery.shouldDeferInitialReport(
                PerfReportData.empty(1_000), false, false));
        assertFalse(PerfReportDelivery.shouldDeferInitialReport(reportAtTick(1L), true, false));
    }

    @Test
    void keepsScreenAndMapSubscriptionsIndependent() {
        UUID screen = UUID.randomUUID();
        UUID map = UUID.randomUUID();
        UUID both = UUID.randomUUID();
        var subscriptions = new PerfReportDelivery.ViewerSubscriptions();
        subscriptions.openScreen(screen);
        subscriptions.openScreen(both, true);
        assertFalse(subscriptions.hasMapRecipients());
        subscriptions.setMap(map, true);
        subscriptions.setMap(both, true);

        assertTrue(subscriptions.hasMapRecipients());
        assertEquals(Set.of(screen, map, both), subscriptions.recipients());
        assertTrue(subscriptions.isLiveViewer(screen));
        assertTrue(subscriptions.isLiveViewer(map));
        assertTrue(subscriptions.isLiveViewer(both));
        assertEquals(PerfReportPayload.Mode.OPEN, subscriptions.nextMode(both));
        assertEquals(PerfReportPayload.Mode.UPDATE, subscriptions.nextMode(both));
        subscriptions.setMap(both, false);
        assertTrue(subscriptions.isScreenViewer(both));
        assertFalse(subscriptions.isMapViewer(both));
        assertEquals(Set.of(screen, map, both), subscriptions.recipients());

        subscriptions.remove(map);
        assertFalse(subscriptions.isLiveViewer(map));
        assertEquals(Set.of(screen, both), subscriptions.recipients());
    }

    @Test
    void revokedMapPermissionDropsOnlyTheMapSubscription() {
        UUID mapOnly = UUID.randomUUID();
        UUID screenAndMap = UUID.randomUUID();
        var subscriptions = new PerfReportDelivery.ViewerSubscriptions();
        subscriptions.setMap(mapOnly, true);
        subscriptions.openScreen(screenAndMap);
        subscriptions.setMap(screenAndMap, true);

        assertFalse(subscriptions.retainPermittedLiveViewer(mapOnly, false));
        assertFalse(subscriptions.isMapViewer(mapOnly));
        assertTrue(subscriptions.retainPermittedLiveViewer(screenAndMap, false));
        assertFalse(subscriptions.isMapViewer(screenAndMap));
        assertTrue(subscriptions.isScreenViewer(screenAndMap));
    }

    private static PerfReportData reportAtTick(long tickCount) {
        return new PerfReportData(10.0D, 1_000L, 123_000L, tickCount, 10L, 1_000,
                0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
