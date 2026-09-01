package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UnknownStackClusterer {
    public static final int TOP_LIMIT = 3;
    public static final int MAX_FRAME_TEXT_LENGTH = 512;
    private static final String EMPTY_ANCHOR = "<no business frame>";
    private static final Comparator<AnchorStats> AVERAGE_ORDER =
            Comparator.comparingInt(AnchorStats::sampleCount).reversed()
                    .thenComparingInt(AnchorStats::representativeDepth)
                    .thenComparing(AnchorStats::anchor);
    private static final Comparator<AnchorStats> BURST_ORDER =
            Comparator.comparingDouble(AnchorStats::estimatedBurstSamples).reversed()
                    .thenComparing(AVERAGE_ORDER);

    private UnknownStackClusterer() {
    }

    public static Result cluster(UnknownStackSnapshot snapshot) {
        Map<String, AnchorStats> groups = collect(snapshot);
        Map<Long, TickRetention> retainedTicks = retainedTicks(snapshot);
        List<AnchorStats> values = new ArrayList<>(groups.values());
        for (AnchorStats group : values) {
            group.prepare(snapshot, retainedTicks);
        }
        List<Cluster> average = top(values, AVERAGE_ORDER);
        List<Cluster> burst = top(values, BURST_ORDER);
        return new Result(snapshot.reportId(), snapshot.revision(), snapshot.totalObserved(),
                snapshot.retainedCount(), average, burst);
    }

    private static Map<String, AnchorStats> collect(UnknownStackSnapshot snapshot) {
        Map<String, AnchorStats> groups = new HashMap<>();
        for (int index = 0; index < snapshot.retainedCount(); index++) {
            StackTraceElement[] stack = snapshot.stackAt(index);
            if (stack == null || stack.length == 0) {
                continue;
            }
            String anchor = anchor(stack);
            AnchorStats group = groups.get(anchor);
            if (group == null) {
                group = new AnchorStats(anchor);
                groups.put(anchor, group);
            }
            group.accept(stack, snapshot.tickIdAt(index));
        }
        return groups;
    }

    private static Map<Long, TickRetention> retainedTicks(UnknownStackSnapshot snapshot) {
        Map<Long, TickRetention> ticks = new HashMap<>();
        for (int index = 0; index < snapshot.retainedCount(); index++) {
            long tickId = snapshot.tickIdAt(index);
            TickRetention retention = ticks.get(tickId);
            if (retention == null) {
                retention = new TickRetention();
                ticks.put(tickId, retention);
            }
            retention.retain(snapshot.tickTotalAt(index));
        }
        return ticks;
    }

    private static List<Cluster> top(List<AnchorStats> groups,
                                     Comparator<AnchorStats> order) {
        return groups.stream().sorted(order).limit(TOP_LIMIT)
                .map(AnchorStats::toCluster).toList();
    }

    private static String anchor(StackTraceElement[] stack) {
        String first = null;
        for (StackTraceElement frame : stack) {
            if (isInfrastructure(frame)) {
                continue;
            }
            String normalized = normalizedFrame(frame);
            if (first == null) {
                first = normalized;
            } else {
                return first + " -> " + normalized;
            }
        }
        return first == null ? EMPTY_ANCHOR : first;
    }

    private static boolean isInfrastructure(StackTraceElement frame) {
        String className = visibleClassName(frame.getClassName());
        String method = frame.getMethodName();
        if (isWaitingMethod(method)) {
            return true;
        }
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("net.neoforged.bus.")
                || className.equals("net.neoforged.neoforge.event.EventHooks")
                || className.startsWith("net.minecraft.util.thread.")
                || className.equals("net.minecraft.server.MinecraftServer")
                || className.equals("net.minecraft.server.dedicated.DedicatedServer");
    }

    private static boolean isWaitingMethod(String method) {
        return method.equals("wait") || method.equals("sleep") || method.equals("park")
                || method.equals("parkNanos") || method.equals("managedBlock")
                || method.equals("waitForTasks") || method.equals("waitUntilNextTick");
    }

    private static String normalizedPath(StackTraceElement[] stack) {
        StringBuilder result = new StringBuilder(stack.length * 48);
        for (StackTraceElement frame : stack) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(normalizedFrame(frame));
        }
        return result.toString();
    }

    private static String normalizedFrame(StackTraceElement frame) {
        return visibleClassName(frame.getClassName()) + "#" + frame.getMethodName();
    }

    private static String visibleClassName(String className) {
        int hiddenSuffix = className.indexOf('/');
        return hiddenSuffix < 0 ? className : className.substring(0, hiddenSuffix);
    }

    private static List<String> rawFrames(StackTraceElement[] stack) {
        List<String> frames = new ArrayList<>(stack.length);
        for (StackTraceElement frame : stack) {
            frames.add(limit(frame.toString(), MAX_FRAME_TEXT_LENGTH));
        }
        return List.copyOf(frames);
    }

    private static String rawKey(StackTraceElement[] stack) {
        StringBuilder result = new StringBuilder(stack.length * 64);
        for (StackTraceElement frame : stack) {
            result.append(frame).append('\n');
        }
        return result.toString();
    }

    private static String limit(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public record Result(long reportId,
                         long revision,
                         long totalObserved,
                         int retainedCount,
                         List<Cluster> average,
                         List<Cluster> burst) {
        public Result {
            average = List.copyOf(average);
            burst = List.copyOf(burst);
        }
    }

    public record Cluster(String anchor,
                          int sampleCount,
                          int representativeCount,
                          int peakSampleCount,
                          double estimatedAverageMillis,
                          double estimatedBurstMillis,
                          List<String> frames) {
        public Cluster {
            frames = List.copyOf(frames);
        }
    }

    private static final class AnchorStats {
        private final String anchor;
        private final Map<String, PathStats> paths = new HashMap<>();
        private final Map<Long, Integer> tickCounts = new HashMap<>();
        private int sampleCount;
        private int peakSampleCount;
        private double estimatedBurstSamples;
        private PathStats representative;
        private Cluster cluster;

        private AnchorStats(String anchor) {
            this.anchor = anchor;
        }

        void accept(StackTraceElement[] stack, long tickId) {
            sampleCount++;
            int tickCount = tickCounts.getOrDefault(tickId, 0) + 1;
            tickCounts.put(tickId, tickCount);
            peakSampleCount = Math.max(peakSampleCount, tickCount);
            String path = normalizedPath(stack);
            PathStats stats = paths.get(path);
            if (stats == null) {
                paths.put(path, new PathStats(path, stack));
            } else {
                stats.accept(stack);
            }
        }

        void prepare(UnknownStackSnapshot snapshot, Map<Long, TickRetention> retainedTicks) {
            representative = selectRepresentative();
            estimatedBurstSamples = estimateBurstSamples(retainedTicks);
            double scale = snapshot.retainedCount() == 0 ? 0.0D
                    : snapshot.totalObserved() / (double) snapshot.retainedCount();
            double intervalMillis = snapshot.intervalMicros() / 1_000.0D;
            double average = snapshot.tickCount() == 0L ? 0.0D
                    : sampleCount * scale * intervalMillis / snapshot.tickCount();
            double burst = estimatedBurstSamples * intervalMillis;
            cluster = new Cluster(limit(anchor, MAX_FRAME_TEXT_LENGTH), sampleCount,
                    representative.count, peakSampleCount, average, burst,
                    rawFrames(representative.stack));
        }

        Cluster toCluster() {
            return cluster;
        }

        int sampleCount() {
            return sampleCount;
        }

        double estimatedBurstSamples() {
            return estimatedBurstSamples;
        }

        int representativeDepth() {
            return representative.stack.length;
        }

        String anchor() {
            return anchor;
        }

        private PathStats selectRepresentative() {
            return paths.values().stream().sorted(PathStats.ORDER).findFirst().orElseThrow();
        }

        private double estimateBurstSamples(Map<Long, TickRetention> retainedTicks) {
            double peak = 0.0D;
            for (Map.Entry<Long, Integer> tick : tickCounts.entrySet()) {
                TickRetention retention = retainedTicks.get(tick.getKey());
                if (retention == null) {
                    continue;
                }
                double estimate = tick.getValue() * retention.totalSamples()
                        / (double) retention.retainedSamples;
                peak = Math.max(peak, estimate);
            }
            return peak;
        }
    }

    private static final class TickRetention {
        private long reportedTotal;
        private int retainedSamples;

        void retain(long totalSamples) {
            retainedSamples++;
            reportedTotal = Math.max(reportedTotal, totalSamples);
        }

        long totalSamples() {
            return Math.max(reportedTotal, retainedSamples);
        }
    }

    private static final class PathStats {
        private static final Comparator<PathStats> ORDER =
                Comparator.comparingInt((PathStats path) -> path.count).reversed()
                        .thenComparingInt(path -> path.stack.length)
                        .thenComparing(path -> path.path);
        private final String path;
        private StackTraceElement[] stack;
        private String rawKey;
        private int count = 1;

        private PathStats(String path, StackTraceElement[] stack) {
            this.path = path;
            this.stack = stack;
            rawKey = UnknownStackClusterer.rawKey(stack);
        }

        void accept(StackTraceElement[] candidate) {
            count++;
            String candidateKey = UnknownStackClusterer.rawKey(candidate);
            if (candidateKey.compareTo(rawKey) < 0) {
                stack = candidate;
                rawKey = candidateKey;
            }
        }
    }
}
