package com.moepus.serverwarashi.common.modules.performance.sampling;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerDetailAccumulatorTest {
    @Test
    void evidenceAcrossTicksRefinesAllPrimaryHandlerSamples() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(1L);
        accept(aggregator, handlerTick(3, handlerStack("ExampleEvents", "first")));

        PerfReportData early = aggregator.build(50_000_000L, 3L, 1_000);
        assertEquals(3L, samples(early, "Handler"));

        accept(aggregator, handlerTick(5, handlerStack("ExampleEvents", "first")));
        PerfReportData refined = aggregator.build(100_000_000L, 8L, 1_000);

        assertEquals(8L, samples(refined,
                "ExampleEvents#first(ServerTickEvent.Pre)"));
        assertEquals(0L, samples(refined, "Handler"));
        assertEquals(8L, handlerSamples(refined));
    }

    @Test
    void oneEvidenceSampleLeavesCrossTickHandlerWeightGeneric() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(2L);
        accept(aggregator, handlerTick(4, handlerStack("ExampleEvents", "first")));
        accept(aggregator, handlerTick(6));

        PerfReportData report = aggregator.build(100_000_000L, 10L, 1_000);

        assertEquals(10L, samples(report, "Handler"));
        assertEquals(10L, handlerSamples(report));
    }

    @Test
    void crossTickRatiosPreserveHandlerWeightAndOriginalTickMaximums() {
        PerfSampleAggregator aggregator = new PerfSampleAggregator(3L);
        accept(aggregator, handlerTick(4, handlerStack("FirstEvents", "run")));
        accept(aggregator, handlerTick(8,
                handlerStack("SecondEvents", "run"),
                handlerStack("SecondEvents", "run"),
                handlerStack("SecondEvents", "run")));

        PerfReportData report = aggregator.build(100_000_000L, 12L, 1_000);

        assertEquals(3L, samples(report, "FirstEvents#run(ServerTickEvent.Pre)"));
        assertEquals(9L, samples(report, "SecondEvents#run(ServerTickEvent.Pre)"));
        assertEquals(12L, handlerSamples(report));
        assertTrue(entry(report, "SecondEvents#run(ServerTickEvent.Pre)").metric().max()
                > entry(report, "FirstEvents#run(ServerTickEvent.Pre)").metric().max());
    }

    private static void accept(PerfSampleAggregator aggregator, TickSamples tick) {
        tick.applyDetails(new UnknownStackReservoir(9L));
        aggregator.accept(tick);
    }

    private static TickSamples handlerTick(int primarySamples, StackTraceElement[]... details) {
        TickSamples tick = new TickSamples(null);
        PerfWorkTracker.Snapshot generic = new PerfWorkTracker.Snapshot(
                TickType.HANDLER, null, HandlerDetailAccumulatorTest.class, null,
                TickDetailKind.NONE, -1, false, 0, 0, 0, false, "", "Handler");
        for (int sample = 0; sample < primarySamples; sample++) {
            tick.sample(generic);
        }
        for (StackTraceElement[] detail : details) {
            tick.recordDetail(new AsyncStackSample(1L, detail),
                    PrimaryObservationRing.CoarseState.HANDLER);
        }
        return tick;
    }

    private static StackTraceElement[] handlerStack(String owner, String method) {
        return new StackTraceElement[]{
                frame("com.example." + owner, method),
                frame("net.neoforged.bus.EventBus", "post"),
                frame("net.neoforged.neoforge.event.EventHooks", "fireServerTickPre")
        };
    }

    private static StackTraceElement frame(String className, String method) {
        return new StackTraceElement(className, method, "Test.java", 1);
    }

    private static long handlerSamples(PerfReportData report) {
        return report.tickables().stream().filter(entry -> entry.type() == TickType.HANDLER)
                .mapToLong(entry -> entry.metric().samples()).sum();
    }

    private static long samples(PerfReportData report, String name) {
        return report.tickables().stream().filter(entry -> name.equals(entry.className()))
                .mapToLong(entry -> entry.metric().samples()).sum();
    }

    private static PerfReportData.TickableEntry entry(PerfReportData report, String name) {
        return report.tickables().stream().filter(candidate -> name.equals(candidate.className()))
                .findFirst().orElseThrow();
    }
}
