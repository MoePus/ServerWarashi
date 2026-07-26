package com.moepus.serverwarashi.client.screen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfReportPagerTest {
    @Test
    void splitsRowsIntoThirtyItemPagesAndClampsPage() {
        List<Integer> rows = IntStream.range(0, 65).boxed().toList();

        assertEquals(3, PerfReportPager.pageCount(rows.size()));
        assertEquals(30, PerfReportPager.page(rows, 0).size());
        assertEquals(30, PerfReportPager.page(rows, 1).size());
        assertEquals(List.of(60, 61, 62, 63, 64), PerfReportPager.page(rows, 99));
        assertEquals(0, PerfReportPager.clampPage(4, 0));
    }
}
