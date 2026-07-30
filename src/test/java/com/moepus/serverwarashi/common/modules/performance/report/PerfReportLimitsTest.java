package com.moepus.serverwarashi.common.modules.performance.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfReportLimitsTest {
    @Test
    void keepsUnionOfMsptAndMaxLeaders() {
        List<Entry> entries = List.of(
                entry("mspt", 10.0D, 1.0D),
                entry("max", 1.0D, 10.0D),
                entry("other", 2.0D, 2.0D)
        );

        PerfReportLimits.Limited<Entry> limited = PerfReportLimits.topMetricUnion(entries, 1, Entry::metric);

        assertEquals(2, limited.entries().size());
        assertEquals(1, limited.omitted());
        assertTrue(limited.entries().stream().anyMatch(value -> value.id().equals("mspt")));
        assertTrue(limited.entries().stream().anyMatch(value -> value.id().equals("max")));
    }

    private static Entry entry(String id, double mspt, double max) {
        return new Entry(id, new PerfReportData.Metric(mspt, max, 1L));
    }

    private record Entry(String id, PerfReportData.Metric metric) {
    }
}
