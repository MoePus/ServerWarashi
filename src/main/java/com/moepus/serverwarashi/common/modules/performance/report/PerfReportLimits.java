package com.moepus.serverwarashi.common.modules.performance.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

public final class PerfReportLimits {
    public static final int MAX_ENTRIES = 4_096;
    public static final int MAX_LOADER_SOURCES = 4_096;
    public static final int MAX_LOADER_REGIONS = 4_096;
    public static final int MAX_LOADER_REGION_SPAN = 4_096;
    public static final int MAX_CODEC_ENTRIES = 32_768;
    public static final int MAX_STRING_LENGTH = 1_024;
    public static final int MAX_CODEC_STRING_CHARS = 1_048_576;

    private PerfReportLimits() {
    }

    public static <T> Limited<T> topMetricUnion(List<T> entries,
                                                 int perMetricLimit,
                                                 Function<T, PerfReportData.Metric> metric) {
        return topMetricUnion(entries, perMetricLimit, metric, (left, right) -> 0);
    }

    public static <T> Limited<T> topMetricUnion(List<T> entries,
                                                 int perMetricLimit,
                                                 Function<T, PerfReportData.Metric> metric,
                                                 Comparator<T> stableOrder) {
        Comparator<T> byMspt = Comparator
                .comparingDouble((T value) -> metric.apply(value).mspt()).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (T value) -> metric.apply(value).max()).reversed())
                .thenComparing(stableOrder);
        Comparator<T> byMax = Comparator
                .comparingDouble((T value) -> metric.apply(value).max()).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (T value) -> metric.apply(value).mspt()).reversed())
                .thenComparing(stableOrder);
        if (entries.size() <= perMetricLimit) {
            return new Limited<>(entries.stream().sorted(byMspt).toList(), 0);
        }
        LinkedHashSet<T> selected = new LinkedHashSet<>();
        entries.stream().sorted(byMspt).limit(perMetricLimit).forEach(selected::add);
        entries.stream().sorted(byMax).limit(perMetricLimit).forEach(selected::add);
        List<T> result = new ArrayList<>(selected);
        result.sort(byMspt);
        return new Limited<>(List.copyOf(result), entries.size() - result.size());
    }

    public record Limited<T>(List<T> entries, int omitted) {
    }
}
