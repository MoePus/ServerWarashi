package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfSamplerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownStackRequestGateTest {
    @Test
    void enforcesOneInFlightAndOneAcceptedRequestPerSecond() {
        UnknownStackRequestGate gate = new UnknownStackRequestGate();
        UUID player = UUID.randomUUID();

        assertTrue(gate.tryBegin(player, 1L, 100L));
        assertFalse(gate.tryBegin(player, 2L, 2_000_000_000L));
        gate.finish(player, 1L);
        assertFalse(gate.tryBegin(player, 2L,
                100L + UnknownStackRequestGate.REQUEST_INTERVAL_NANOS - 1L));
        assertTrue(gate.tryBegin(player, 2L,
                100L + UnknownStackRequestGate.REQUEST_INTERVAL_NANOS));
        gate.remove(player);
        assertTrue(gate.tryBegin(player, 3L, 101L));
    }

    @Test
    void sharesMatchingComputationAndSeparatesReports() {
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        UnknownStackComputationCache cache = new UnknownStackComputationCache(work::add);
        PerfSamplerSession firstSession = PerfSamplerSession.create(
                1L, 1_000, PerfSamplerSession.Options.unlimited(null));
        PerfSamplerSession secondSession = PerfSamplerSession.create(
                2L, 1_000, PerfSamplerSession.Options.unlimited(null));

        var first = cache.acquire(firstSession.unknownStackSnapshot());
        var shared = cache.acquire(firstSession.unknownStackSnapshot());
        var differentReport = cache.acquire(secondSession.unknownStackSnapshot());

        assertSame(first.future(), shared.future());
        assertSame(first.future(), cache.current(1L, false).future());
        assertNotSame(first.future(), differentReport.future());
        assertEquals(2, work.size());
        assertFalse(first.future().isDone());
        work.removeFirst().run();
        assertTrue(first.future().isDone());
        cache.release(first);
        cache.release(shared);
        cache.release(differentReport);
        firstSession.stop();
        secondSession.stop();
    }

    @Test
    void globallyBoundsDifferentReportComputations() {
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        UnknownStackComputationCache cache = new UnknownStackComputationCache(work::add);
        PerfSamplerSession[] sessions = new PerfSamplerSession[
                UnknownStackComputationCache.MAX_IN_FLIGHT + 1];
        try {
            for (int index = 0; index < sessions.length; index++) {
                sessions[index] = PerfSamplerSession.create(
                        index + 1L, 1_000, PerfSamplerSession.Options.unlimited(null));
            }
            for (int index = 0; index < UnknownStackComputationCache.MAX_IN_FLIGHT; index++) {
                assertTrue(cache.acquire(sessions[index].unknownStackSnapshot()) != null);
            }
            assertTrue(cache.acquire(sessions[sessions.length - 1]
                    .unknownStackSnapshot()) == null);
        } finally {
            for (PerfSamplerSession session : sessions) {
                if (session != null) {
                    session.stop();
                }
            }
        }
    }

    @Test
    void finalReportDoesNotJoinActiveGenerationWithSameReportId() {
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        UnknownStackComputationCache cache = new UnknownStackComputationCache(work::add);
        PerfSamplerSession session = PerfSamplerSession.create(
                30L, 1_000, PerfSamplerSession.Options.unlimited(null));
        var active = cache.acquire(session.unknownStackSnapshot());

        session.stop();
        var finalized = cache.acquire(session.unknownStackSnapshot());

        assertNotSame(active.future(), finalized.future());
    }
}
