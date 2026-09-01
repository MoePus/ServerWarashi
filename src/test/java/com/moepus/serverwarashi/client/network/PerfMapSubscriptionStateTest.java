package com.moepus.serverwarashi.client.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfMapSubscriptionStateTest {
    @Test
    void combinesMapAndLiveSourcesWithoutDuplicateSends() {
        RecordingSender sender = new RecordingSender();
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);

        assertTrue(state.setMapOpen(true));
        assertFalse(state.setMapOpen(true));
        assertFalse(state.setAnalysisLive(true));
        assertTrue(state.setMapOpen(false));
        assertTrue(state.setAnalysisLive(false));

        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(true, true),
                new PerfMapSubscriptionState.Request(false, true),
                new PerfMapSubscriptionState.Request(false, false)), sender.requests);
    }

    @Test
    void retriesAnUnchangedRequestAfterSendFailure() {
        RecordingSender sender = new RecordingSender();
        sender.failNext = true;
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);

        assertFalse(state.setAnalysisLive(true));
        assertTrue(state.setAnalysisLive(true));

        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(false, true),
                new PerfMapSubscriptionState.Request(false, true)), sender.requests);
    }

    @Test
    void authoritativeStopKeepsMapSubscriptionUntilMapCloses() {
        RecordingSender sender = new RecordingSender();
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);

        assertTrue(state.setAnalysisLive(true));
        assertTrue(state.setMapOpen(true));
        assertFalse(state.setAnalysisLive(false));
        assertTrue(state.setMapOpen(false));

        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(false, true),
                new PerfMapSubscriptionState.Request(true, true),
                new PerfMapSubscriptionState.Request(false, false)), sender.requests);
    }

    @Test
    void authoritativeStopEndsWorldOnlySubscription() {
        RecordingSender sender = new RecordingSender();
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);

        assertTrue(state.setAnalysisLive(true));
        assertTrue(state.setAnalysisLive(false));

        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(false, true),
                new PerfMapSubscriptionState.Request(false, false)), sender.requests);
    }

    @Test
    void disablingWorldOverlayEndsOnlyTheWorldOnlySubscription() {
        RecordingSender sender = new RecordingSender();
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);

        assertTrue(state.setAnalysisLive(true));
        assertTrue(state.setWorldOverlayEnabled(false));
        assertTrue(state.setMapOpen(true));
        assertFalse(state.setWorldOverlayEnabled(true));
        assertTrue(state.setMapOpen(false));

        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(false, true),
                new PerfMapSubscriptionState.Request(false, false),
                new PerfMapSubscriptionState.Request(true, true),
                new PerfMapSubscriptionState.Request(false, true)), sender.requests);
    }

    @Test
    void clearResetsAllSourcesAndTransportState() {
        RecordingSender sender = new RecordingSender();
        PerfMapSubscriptionState state = new PerfMapSubscriptionState(sender);
        assertTrue(state.setMapOpen(true));

        state.clear();

        assertFalse(state.setMapOpen(false));
        assertTrue(state.setAnalysisLive(true));
        assertEquals(List.of(
                new PerfMapSubscriptionState.Request(true, true),
                new PerfMapSubscriptionState.Request(false, true)), sender.requests);
    }

    private static final class RecordingSender implements PerfMapSubscriptionState.Sender {
        private final List<PerfMapSubscriptionState.Request> requests = new ArrayList<>();
        private boolean failNext;

        @Override
        public boolean send(PerfMapSubscriptionState.Request request) {
            requests.add(request);
            if (!failNext) {
                return true;
            }
            failNext = false;
            return false;
        }
    }
}
