package com.moepus.serverwarashi.common.modules.performance.sampling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UnknownStackClustererTest {
    @Test
    void separatesAverageFrequencyFromSingleTickBurst() {
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
        assertEquals("com.example.Beta#work -> com.example.Helper#run",
                result.burst().getFirst().anchor());
        assertEquals(3, result.burst().getFirst().peakSampleCount());
        assertEquals(3.0D, result.burst().getFirst().estimatedBurstMillis());
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
        assertEquals(0.5D, result.average().getFirst().estimatedBurstMillis());
        assertEquals(result.average(), UnknownStackClusterer.cluster(snapshot).average());
    }

    @Test
    void globalReservoirScaleDoesNotInflateSingleTickPeak() {
        List<StackTraceElement[]> stacks = new ArrayList<>();
        List<Long> ticks = new ArrayList<>();
        for (long tick = 1L; tick <= 256L; tick++) {
            add(stacks, ticks, beta((int) tick), tick);
        }
        long[] tickTotals = new long[256];
        java.util.Arrays.fill(tickTotals, 1L);
        var metadata = new UnknownStackSnapshot.Metadata(
                8L, 2L, 25_600L, 25_600L, 1_000, true);
        var samples = new UnknownStackSnapshot.Samples(
                stacks.toArray(StackTraceElement[][]::new), toLongArray(ticks), tickTotals);

        UnknownStackClusterer.Result result = UnknownStackClusterer.cluster(
                new UnknownStackSnapshot(metadata, samples));

        assertEquals(1.0D, result.burst().getFirst().estimatedBurstMillis());
        assertEquals(1.0D, result.average().getFirst().estimatedAverageMillis());
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
        Map<Long, Long> totals = new HashMap<>();
        for (long tickId : tickIds) {
            totals.merge(tickId, 1L, Long::sum);
        }
        long[] tickTotals = new long[tickIds.length];
        for (int index = 0; index < tickIds.length; index++) {
            tickTotals[index] = totals.get(tickIds[index]);
        }
        var metadata = new UnknownStackSnapshot.Metadata(
                reportId, 2L, stacks.size(), tickCount, intervalMicros, true);
        var samples = new UnknownStackSnapshot.Samples(
                stacks.toArray(StackTraceElement[][]::new), tickIds, tickTotals);
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
