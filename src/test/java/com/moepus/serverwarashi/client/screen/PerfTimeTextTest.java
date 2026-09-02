package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerfTimeTextTest {
    @Test
    void returnsTranslationComponentsForSessionAndPlayerStatus() {
        long now = 10_000_000L;
        PerfReportData report = new PerfReportData(10.0D, 1_000L,
                now - 60_000L, 20L, 42L, 1_000,
                0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of())
                .withSampling(new PerfReportData.SamplingMetadata("JFR",
                        PerfReportData.DurationSemantics.PRIMARY_SAMPLE_DENSITY,
                        PerfReportData.Confidence.HIGH, 0L, ""));

        Component session = PerfTimeText.session(report, true, now);
        assertEquals("screen.serverwarashi.profiler.status.session", key(session));
        assertEquals("screen.serverwarashi.profiler.status.running",
                key((Component) contents(session).getArgs()[0]));
        assertEquals("screen.serverwarashi.profiler.status.online",
                key(PerfTimeText.player(true, 0L, now)));
        assertEquals("screen.serverwarashi.profiler.time.hours_ago",
                key(PerfTimeText.player(false, now - 3_600_000L, now)));
        assertEquals("screen.serverwarashi.profiler.status.unknown",
                key(PerfTimeText.player(false, 0L, now)));
    }

    private static String key(Component component) {
        return contents(component).getKey();
    }

    private static TranslatableContents contents(Component component) {
        return (TranslatableContents) component.getContents();
    }
}
