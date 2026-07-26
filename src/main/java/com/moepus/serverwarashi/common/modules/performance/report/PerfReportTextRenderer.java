package com.moepus.serverwarashi.common.modules.performance.report;

import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

public final class PerfReportTextRenderer {
    private static final int ENTRY_LIMIT = 10;

    private PerfReportTextRenderer() {
    }

    public static Component render(PerfReportData report) {
        MutableComponent output = Component.literal(String.format(Locale.ROOT,
                "Performance profile: %.2f MSPT | %.1fs | %d ticks | %d samples | %dus",
                report.serverMspt(), report.elapsedMillis() / 1000.0D, report.tickCount(),
                report.totalSamples(), report.intervalMicros())).withStyle(ChatFormatting.AQUA);
        appendTop(output, new Section("Chunks", report.chunks().stream()
                .sorted(byMspt(PerfReportData.ChunkEntry::metric))
                .limit(ENTRY_LIMIT)
                .map(entry -> entry.dimension() + " [" + entry.chunkX() + ", " + entry.chunkZ() + "] "
                        + metric(entry.metric())).toList(),
                omittedAfterTextLimit(report.chunks().size(), report.omittedChunks())));
        appendTop(output, new Section("Tickables", report.tickables().stream()
                .sorted(byMspt(PerfReportData.TickableEntry::metric))
                .limit(ENTRY_LIMIT)
                .map(entry -> tickableIdentity(entry) + " " + metric(entry.metric())).toList(),
                omittedAfterTextLimit(report.tickables().size(), report.omittedTickables())));
        appendTop(output, new Section("Owners", report.owners().stream()
                .sorted(byMspt(PerfReportData.OwnerEntry::metric))
                .limit(ENTRY_LIMIT)
                .map(entry -> entry.name() + " " + metric(entry.metric())).toList(),
                omittedAfterTextLimit(report.owners().size(), 0)));
        return output;
    }

    private static void appendTop(MutableComponent output, Section section) {
        output.append(Component.literal("\n" + section.heading + ":").withStyle(ChatFormatting.GRAY));
        boolean empty = true;
        for (String entry : section.entries) {
            output.append(Component.literal("\n  " + entry));
            empty = false;
        }
        if (empty) {
            output.append(Component.literal(" none").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (section.omitted > 0) {
            output.append(Component.literal("\n  ... " + section.omitted + " omitted")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String metric(PerfReportData.Metric metric) {
        return String.format(Locale.ROOT, "%.3f MSPT, p98 %.3f, %d samples",
                metric.mspt(), metric.p98(), metric.samples());
    }

    private static String tickableIdentity(PerfReportData.TickableEntry entry) {
        String id = entry.type() + " " + entry.className();
        if (entry.type() == TickType.ENTITY) {
            id += "#" + entry.entityId();
        }
        if (entry.type() == TickType.TASK) {
            return id + " @ " + entry.eventLoop();
        }
        if (!entry.positioned()) {
            return id + " @ " + entry.dimension();
        }
        String position = entry.chunkPosition()
                ? "[" + entry.x() + ", " + entry.z() + "]"
                : "(" + entry.x() + ", " + entry.y() + ", " + entry.z() + ")";
        return id + " @ " + entry.dimension() + " " + position;
    }

    private static <T> Comparator<T> byMspt(Function<T, PerfReportData.Metric> metric) {
        return Comparator.comparingDouble((T value) -> metric.apply(value).mspt()).reversed();
    }

    private static int omittedAfterTextLimit(int retained, int upstreamOmitted) {
        long omitted = (long) upstreamOmitted + Math.max(0, retained - ENTRY_LIMIT);
        return (int) Math.min(Integer.MAX_VALUE, omitted);
    }

    private record Section(String heading, Iterable<String> entries, int omitted) {
    }
}
