package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownStackClustererTest {
    @Test
    void clustersByAverageFrequencyAndSelectsRepresentativePath() {
        List<StackTraceElement[]> stacks = new ArrayList<>();
        List<Long> ticks = new ArrayList<>();
        add(stacks, ticks, alpha("java.lang.reflect.Method", 10), 1L);
        add(stacks, ticks, alpha("java.lang.reflect.Method", 20), 2L);
        add(stacks, ticks, alpha("java.lang.reflect.Method", 30), 3L);
        add(stacks, ticks, alpha("jdk.internal.reflect.NativeMethodAccessorImpl", 40), 4L);
        add(stacks, ticks, beta(50), 2L);
        add(stacks, ticks, beta(60), 2L);
        add(stacks, ticks, beta(70), 2L);
        UnknownStackSnapshot snapshot = snapshot(11L, 4L, 1_000, stacks, ticks);

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(snapshot);

        assertEquals("com.example.Alpha#work -> com.example.Helper#run",
                result.average().getFirst().anchor());
        assertEquals(4, result.average().getFirst().sampleCount());
        assertEquals(3, result.average().getFirst().representativeCount());
        assertEquals(1.0D, result.average().getFirst().estimatedAverageMillis());
        assertFalse(result.average().getFirst().anchor().contains("0x123"));
        assertEquals("com.example.Alpha/0x123.work(Alpha.java:10)",
                result.average().getFirst().frames().get(1));
    }

    @Test
    void stableTieBreakUsesDepthThenNormalizedText() {
        StackTraceElement[] deeper = stack(
                frame("com.example.Alpha", "run", 1),
                frame("com.example.Helper", "call", 2),
                frame("com.example.More", "call", 3));
        StackTraceElement[] lexical = stack(
                frame("com.example.Beta", "run", 1),
                frame("com.example.Helper", "call", 2));
        UnknownStackSnapshot snapshot = snapshot(4L, 1L, 500,
                List.of(deeper, lexical), List.of(1L, 1L));

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(snapshot);

        assertEquals("com.example.Beta#run -> com.example.Helper#call",
                result.average().getFirst().anchor());
        assertEquals(0.5D, result.average().getFirst().estimatedAverageMillis());
        assertEquals(result.average(), UnknownStackClusterer.cluster(snapshot).average());
    }

    @Test
    void reliableJfrSharesDistributeAllPrimaryUnknownWeight() {
        var metadata = new UnknownStackSnapshot.Metadata(
                9L, 2L, 2L, 100L, 10L, 1_000, true);
        var samples = new UnknownStackSnapshot.Samples(
                new StackTraceElement[][]{alpha("java.lang.reflect.Method", 1), beta(2)},
                new long[]{1L, 2L});

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(
                new UnknownStackSnapshot(metadata, samples));

        assertEquals(5.0D, result.average().getFirst().estimatedAverageMillis());
        assertEquals(10.0D, result.average().stream()
                .mapToDouble(UnknownStackClusterer.Cluster::estimatedAverageMillis).sum());
    }

    @Test
    void singleObservationIsNotExtrapolatedAcrossPrimaryUnknown() {
        var metadata = new UnknownStackSnapshot.Metadata(
                10L, 2L, 1L, 100L, 10L, 1_000, true);
        var samples = new UnknownStackSnapshot.Samples(
                new StackTraceElement[][]{alpha("java.lang.reflect.Method", 1)},
                new long[]{1L});

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(
                new UnknownStackSnapshot(metadata, samples));

        assertTrue(result.average().isEmpty());
    }

    @Test
    void sourceLessJfrFramesDoNotAddUnknownSourceText() {
        StackTraceElement sourceLess = new StackTraceElement(
                "com.example.GeneratedWork", "run", null, 42);
        UnknownStackSnapshot snapshot = snapshot(12L, 2L, 1_000,
                List.of(stack(sourceLess), stack(sourceLess)), List.of(1L, 2L));

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(snapshot);

        assertEquals("com.example.GeneratedWork.run",
                result.average().getFirst().frames().getFirst());
        assertFalse(result.average().getFirst().frames().getFirst().contains("Unknown Source"));
    }

    private static StackTraceElement[] alpha(String wrapper, int line) {
        return stack(
                frame(wrapper, "invoke", line),
                new StackTraceElement("com.example.Alpha/0x123", "work", "Alpha.java", line),
                frame("com.example.Helper", "run", line),
                frame("net.minecraft.server.MinecraftServer", "tickServer", line));
    }

    private static StackTraceElement[] beta(int line) {
        return stack(
                frame("java.lang.invoke.MethodHandle", "invoke", line),
                frame("com.example.Beta", "work", line),
                frame("com.example.Helper", "run", line));
    }

    private static void add(List<StackTraceElement[]> stacks,
                            List<Long> ticks,
                            StackTraceElement[] stack,
                            long tick) {
        stacks.add(stack);
        ticks.add(tick);
    }

    private static UnknownStackSnapshot snapshot(long reportId,
                                                  long tickCount,
                                                  int intervalMicros,
                                                  List<StackTraceElement[]> stacks,
                                                  List<Long> ticks) {
        long[] tickIds = toLongArray(ticks);
        var metadata = new UnknownStackSnapshot.Metadata(
                reportId, 2L, stacks.size(), stacks.size(),
                tickCount, intervalMicros, true);
        var samples = new UnknownStackSnapshot.Samples(
                stacks.toArray(StackTraceElement[][]::new), tickIds);
        return new UnknownStackSnapshot(metadata, samples);
    }

    private static long[] toLongArray(List<Long> ticks) {
        long[] result = new long[ticks.size()];
        for (int index = 0; index < ticks.size(); index++) {
            result[index] = ticks.get(index);
        }
        return result;
    }

    private static StackTraceElement[] stack(StackTraceElement... frames) {
        return frames;
    }

    private static StackTraceElement frame(String className, String method, int line) {
        return new StackTraceElement(className, method, "Test.java", line);
    }
}
