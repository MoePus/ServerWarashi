package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.network.UnknownStackDetailsPayload;
import com.moepus.serverwarashi.common.network.UnknownStackDetailsRequestPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnknownStackCopyControllerTest {
    @BeforeEach
    void clearBefore() {
        UnknownStackCopyController.clearRuntimeState();
    }

    @AfterEach
    void clearAfter() {
        UnknownStackCopyController.clearRuntimeState();
    }

    @Test
    void oneClickRequestsAndMatchingResponseCopiesAutomatically() {
        Fixture fixture = new Fixture(9L, true);
        fixture.controller.activate();

        assertTrue(fixture.controller.request());
        assertFalse(fixture.controller.request());
        assertEquals(1, fixture.transport.requests.size());
        long requestId = fixture.transport.requests.getFirst().requestId();
        UnknownStackCopyController.handlePayload(response(requestId, 9L));

        assertTrue(fixture.output.clipboard.contains("Average-time Top 3"));
        assertTrue(fixture.output.clipboard.contains("Burst-time Top 3"));
        assertEquals(2, fixture.output.notifiedCount);
        assertEquals(0L, fixture.controller.pendingRequestId());
    }

    @Test
    void timeoutAndOutOfOrderResponsesNeverTouchClipboard() {
        Fixture fixture = new Fixture(10L, true);
        fixture.controller.activate();
        fixture.controller.request();
        long requestId = fixture.controller.pendingRequestId();

        UnknownStackCopyController.handlePayload(response(requestId + 1L, 10L));
        assertEquals("", fixture.output.clipboard);
        fixture.clock.set(UnknownStackCopyController.TIMEOUT_NANOS);
        fixture.controller.tick();
        assertEquals(0L, fixture.controller.pendingRequestId());
        UnknownStackCopyController.handlePayload(response(requestId, 10L));
        assertEquals("", fixture.output.clipboard);
        assertTrue(fixture.controller.tooltip().contains("unknown.timeout"));
        assertTrue(fixture.controller.request());
        assertEquals(2, fixture.transport.requests.size());
    }

    @Test
    void closeAndReportSwitchDiscardDelayedResponses() {
        Fixture closed = new Fixture(11L, true);
        closed.controller.activate();
        closed.controller.request();
        long closedRequest = closed.controller.pendingRequestId();
        closed.controller.close();
        UnknownStackCopyController.handlePayload(response(closedRequest, 11L));
        assertEquals("", closed.output.clipboard);

        Fixture switched = new Fixture(12L, true);
        switched.controller.activate();
        switched.controller.request();
        long switchedRequest = switched.controller.pendingRequestId();
        switched.controller.updateReport(13L, true);
        UnknownStackCopyController.handlePayload(response(switchedRequest, 12L));
        assertEquals("", switched.output.clipboard);
    }

    @Test
    void stoppingSameReportCancelsActiveGenerationRequest() {
        Fixture fixture = new Fixture(18L, true);
        fixture.controller.activate();
        fixture.controller.request();
        long requestId = fixture.controller.pendingRequestId();

        fixture.controller.updateReport(18L, false);
        UnknownStackCopyController.handlePayload(response(requestId, 18L));

        assertEquals(0L, fixture.controller.pendingRequestId());
        assertEquals("", fixture.output.clipboard);
    }

    @Test
    void stoppedReportReusesMostRecentSuccessfulDetails() {
        Fixture first = new Fixture(14L, false);
        first.controller.activate();
        first.controller.request();
        UnknownStackCopyController.handlePayload(response(
                first.controller.pendingRequestId(), 14L));
        first.controller.close();

        Fixture reopened = new Fixture(14L, false);
        reopened.controller.activate();
        assertTrue(reopened.controller.request());

        assertEquals(0, reopened.transport.requests.size());
        assertFalse(reopened.output.clipboard.isEmpty());
    }

    @Test
    void newReportDoesNotRetainStoppedReportDetails() throws Exception {
        Fixture stopped = new Fixture(16L, false);
        stopped.controller.activate();
        stopped.controller.request();
        UnknownStackCopyController.handlePayload(response(
                stopped.controller.pendingRequestId(), 16L));
        stopped.controller.close();

        Fixture next = new Fixture(17L, true);
        next.controller.activate();

        Field cached = UnknownStackCopyController.class.getDeclaredField("recentStopped");
        cached.setAccessible(true);
        assertNull(cached.get(null));
        assertTrue(next.controller.request());
        assertEquals(1, next.transport.requests.size());
        assertEquals("", next.output.clipboard);
    }

    @Test
    void failureResponseIsRetryable() {
        Fixture fixture = new Fixture(15L, true);
        fixture.controller.activate();
        fixture.controller.request();
        long requestId = fixture.controller.pendingRequestId();
        UnknownStackCopyController.handlePayload(UnknownStackDetailsPayload.failed(
                requestId, 15L, UnknownStackDetailsPayload.Status.BUSY));

        assertTrue(fixture.controller.tooltip().contains("unknown.busy"));
        assertTrue(fixture.controller.request());
    }

    private static UnknownStackDetailsPayload response(long requestId, long reportId) {
        UnknownStackDetailsPayload.Cluster cluster = new UnknownStackDetailsPayload.Cluster(
                "example.Work#run", 4, 3, 2, 1.0D, 2.0D,
                List.of("example.Work.run(Work.java:10)"));
        return new UnknownStackDetailsPayload(requestId, reportId, 2L,
                UnknownStackDetailsPayload.Status.OK, 4L, 4,
                List.of(cluster), List.of(cluster));
    }

    private static final class Fixture {
        private final AtomicLong clock = new AtomicLong();
        private final FakeTransport transport = new FakeTransport();
        private final FakeOutput output = new FakeOutput();
        private final UnknownStackCopyController controller;

        private Fixture(long reportId, boolean profiling) {
            controller = UnknownStackCopyController.createForTest(reportId, profiling,
                    new UnknownStackCopyController.Dependencies(transport, output, clock::get));
        }
    }

    private static final class FakeTransport implements UnknownStackCopyController.Transport {
        private final List<UnknownStackDetailsRequestPayload> requests = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean send(UnknownStackDetailsRequestPayload request) {
            requests.add(request);
            return true;
        }
    }

    private static final class FakeOutput implements UnknownStackCopyController.Output {
        private String clipboard = "";
        private int notifiedCount;

        @Override
        public void copy(String text) {
            clipboard = text;
        }

        @Override
        public void notifyCopied(int count) {
            notifiedCount = count;
        }
    }
}
