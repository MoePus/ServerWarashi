package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import net.minecraft.network.chat.Component;

import java.util.Locale;

final class PerfTimeText {
    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    private PerfTimeText() {
    }

    static Component session(PerfReportData report, boolean profiling, long nowMillis) {
        Component time = profiling
                ? Component.translatable("screen.serverwarashi.profiler.status.running")
                : relative(report.generatedAtEpochMillis(), nowMillis,
                "screen.serverwarashi.profiler.status.no_sample");
        String samples = String.format(Locale.ROOT, "%,d", report.totalSamples());
        String confidence = report.sampling().confidence().name().toLowerCase(Locale.ROOT);
        if (!report.sampling().degradationReason().isEmpty()) {
            return Component.translatable("screen.serverwarashi.profiler.status.session_degraded",
                    time, samples, report.sampling().backend(), confidence,
                    report.sampling().degradationReason());
        }
        return Component.translatable("screen.serverwarashi.profiler.status.session",
                time, samples, report.sampling().backend(), confidence);
    }

    static Component player(boolean online, long lastLoginMillis, long nowMillis) {
        return online
                ? Component.translatable("screen.serverwarashi.profiler.status.online")
                : relative(lastLoginMillis, nowMillis, "screen.serverwarashi.profiler.status.unknown");
    }

    static Component relative(long timestampMillis, long nowMillis, String missingKey) {
        if (timestampMillis <= 0L) {
            return Component.translatable(missingKey);
        }
        long elapsed = Math.max(0L, nowMillis - timestampMillis);
        if (elapsed < MINUTE_MILLIS) {
            return Component.translatable("screen.serverwarashi.profiler.time.just_now");
        }
        if (elapsed < HOUR_MILLIS) {
            return relative("minutes", elapsed / MINUTE_MILLIS);
        }
        if (elapsed < DAY_MILLIS) {
            return relative("hours", elapsed / HOUR_MILLIS);
        }
        return relative("days", elapsed / DAY_MILLIS);
    }

    private static Component relative(String unit, long value) {
        return Component.translatable("screen.serverwarashi.profiler.time." + unit + "_ago", value);
    }
}
