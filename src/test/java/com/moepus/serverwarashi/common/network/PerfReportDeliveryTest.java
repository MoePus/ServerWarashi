package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.moepus.serverwarashi.testsupport.PerfReportFixtures.reportBuilder;

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
    void requiresChannelAndResolvedPermissionForMapSubscription() {
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
        var subscriptions = new PerfViewerSubscriptions();
        subscriptions.openScreen(screen);
        subscriptions.openScreen(both, true);
        assertFalse(subscriptions.hasMapRecipients());
        subscriptions.setMap(map, true);
        subscriptions.setMap(both, true);
        subscriptions.setWorld(map, true);
        subscriptions.setWorld(both, true);

        assertTrue(subscriptions.hasMapRecipients());
        assertEquals(Set.of(screen, map, both), subscriptions.recipients());
        assertTrue(subscriptions.isLiveViewer(screen));
        assertTrue(subscriptions.isLiveViewer(map));
        assertTrue(subscriptions.isLiveViewer(both));
        assertTrue(subscriptions.isWorldViewer(both));
        assertFalse(subscriptions.receivesWorldProjection(both));
        assertEquals(PerfReportPayload.Mode.OPEN, subscriptions.nextMode(both));
        assertEquals(PerfReportPayload.Mode.UPDATE, subscriptions.nextMode(both));
        subscriptions.setMap(both, false);
        assertTrue(subscriptions.isScreenViewer(both));
        assertFalse(subscriptions.isMapViewer(both));
        assertTrue(subscriptions.isWorldViewer(both));
        assertFalse(subscriptions.receivesWorldProjection(both));
        assertEquals(Set.of(screen, map, both), subscriptions.recipients());

        subscriptions.closeScreen(both);
        assertFalse(subscriptions.isScreenViewer(both));
        assertTrue(subscriptions.isWorldViewer(both));
        assertEquals(PerfReportPayload.Mode.UPDATE, subscriptions.nextMode(both));

        subscriptions.remove(map);
        assertFalse(subscriptions.isLiveViewer(map));
        assertEquals(Set.of(screen, both), subscriptions.recipients());
    }

    @Test
    void revokedPermissionDropsEveryLiveSubscription() {
        UUID mapOnly = UUID.randomUUID();
        UUID screenAndMap = UUID.randomUUID();
        var subscriptions = new PerfViewerSubscriptions();
        subscriptions.setMap(mapOnly, true);
        subscriptions.setWorld(mapOnly, true);
        subscriptions.openScreen(screenAndMap);
        subscriptions.setMap(screenAndMap, true);
        subscriptions.setWorld(screenAndMap, true);

        assertFalse(subscriptions.retainPermittedLiveViewer(mapOnly, false));
        assertFalse(subscriptions.isMapViewer(mapOnly));
        assertFalse(subscriptions.isWorldViewer(mapOnly));
        assertFalse(subscriptions.retainPermittedLiveViewer(screenAndMap, false));
        assertFalse(subscriptions.isMapViewer(screenAndMap));
        assertFalse(subscriptions.isWorldViewer(screenAndMap));
        assertFalse(subscriptions.isScreenViewer(screenAndMap));
    }

    @Test
    void worldOnlyViewerReceivesCompactChunkProjection() {
        UUID worldOnly = UUID.randomUUID();
        var subscriptions = new PerfViewerSubscriptions();

        subscriptions.setWorld(worldOnly, true);

        assertTrue(subscriptions.receivesWorldHotspots(worldOnly));
        assertTrue(subscriptions.receivesWorldProjection(worldOnly));
        subscriptions.setMap(worldOnly, true);
        assertFalse(subscriptions.receivesWorldProjection(worldOnly));
    }

    @Test
    void dispatcherRoutesWorldOnlyAndMapViewersWithoutDuplicateProjection() {
        UUID viewer = UUID.randomUUID();
        var subscriptions = new PerfViewerSubscriptions();
        var sender = new RecordingUpdateSender();
        var dispatcher = new PerfViewerUpdateDispatcher<>(subscriptions, sender);
        PerfReportData report = reportAtTick(20L);
        PerfWorldHotspotIndex hotspots = PerfWorldHotspotIndex.empty(1L, 1L, 20L);
        var update = new PerfViewerUpdateDispatcher.Update(report, hotspots, true);

        subscriptions.setWorld(viewer, true);
        dispatcher.dispatch(viewer, "player", update);
        assertEquals(List.of("spatial:WORLD", "hotspots"), sender.calls);

        sender.calls.clear();
        subscriptions.setMap(viewer, true);
        dispatcher.dispatch(viewer, "player", update);
        assertEquals(List.of("spatial:MAP", "hotspots"), sender.calls);
    }

    private static PerfReportData reportAtTick(long tickCount) {
        return reportBuilder()
                .serverTiming(10.0D, 1_000L, 123_000L)
                .sampleWindow(tickCount, 10L, 1_000)
                .build();
    }

    private static final class RecordingUpdateSender
            implements PerfViewerUpdateDispatcher.Sender<String> {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void report(String recipient, PerfReportData report,
                           PerfReportPayload.Mode mode, boolean profiling) {
            calls.add("report:" + mode);
        }

        @Override
        public void spatial(String recipient, PerfReportData report,
                            PerfViewerUpdateDispatcher.SpatialTarget target,
                            boolean profiling) {
            calls.add("spatial:" + target);
        }

        @Override
        public void hotspots(String recipient, PerfWorldHotspotIndex hotspots) {
            calls.add("hotspots");
        }
    }
}
