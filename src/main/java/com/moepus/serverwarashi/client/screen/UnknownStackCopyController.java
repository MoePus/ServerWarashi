package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.client.network.UnknownStackDetailsClient;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import com.moepus.serverwarashi.common.network.UnknownStackDetailsPayload;
import com.moepus.serverwarashi.common.network.UnknownStackDetailsRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class UnknownStackCopyController {
    static final long TIMEOUT_NANOS = 10_000_000_000L;
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
    private static UnknownStackCopyController active;
    private static UnknownStackDetailsPayload recentStopped;
    private final Dependencies dependencies;
    private long reportId;
    private long pendingRequestId;
    private long deadlineNanos;
    private boolean profiling;
    private boolean open;
    private UnknownSummary unknownSummary;
    private TooltipState tooltipState = TooltipState.READY;

    private UnknownStackCopyController(long reportId,
                                       boolean profiling,
                                       UnknownSummary unknownSummary,
                                       Dependencies dependencies) {
        this.reportId = reportId;
        this.profiling = profiling;
        this.unknownSummary = unknownSummary;
        this.dependencies = dependencies;
    }

    static UnknownStackCopyController production(PerfReportData report, boolean profiling) {
        return new UnknownStackCopyController(report.reportId(), profiling,
                UnknownSummary.from(report),
                new Dependencies(new ProductionTransport(), new ProductionOutput(), System::nanoTime));
    }

    static UnknownStackCopyController createForTest(
            long reportId, boolean profiling, Dependencies dependencies) {
        return new UnknownStackCopyController(reportId, profiling, null, dependencies);
    }

    public static void handlePayload(UnknownStackDetailsPayload payload) {
        UnknownStackCopyController controller = active;
        if (controller != null) {
            controller.accept(payload);
        }
    }

    public static void clearRuntimeState() {
        if (active != null) {
            active.close();
        }
        recentStopped = null;
    }

    void activate() {
        if (active != null && active != this) {
            active.close();
        }
        clearMismatchedCache(reportId);
        open = true;
        active = this;
    }

    void close() {
        open = false;
        clearPending();
        if (active == this) {
            active = null;
        }
    }

    void updateReport(long nextReportId, boolean nextProfiling) {
        updateIdentity(nextReportId, nextProfiling);
        unknownSummary = null;
    }

    void updateReport(PerfReportData report, boolean nextProfiling) {
        updateIdentity(report.reportId(), nextProfiling);
        unknownSummary = UnknownSummary.from(report);
    }

    private void updateIdentity(long nextReportId, boolean nextProfiling) {
        if (reportId != nextReportId || profiling != nextProfiling) {
            clearPending();
            tooltipState = TooltipState.READY;
        }
        if (reportId != nextReportId) {
            clearMismatchedCache(nextReportId);
        }
        reportId = nextReportId;
        profiling = nextProfiling;
        if (nextReportId == 0L) {
            recentStopped = null;
        }
    }

    boolean available() {
        return reportId > 0L && dependencies.transport.available();
    }

    boolean request() {
        expireIfNeeded();
        if (!open || !available() || pendingRequestId != 0L) {
            return false;
        }
        if (!profiling && recentStopped != null && recentStopped.reportId() == reportId) {
            copy(recentStopped);
            return true;
        }
        long requestId = NEXT_REQUEST_ID.incrementAndGet();
        pendingRequestId = requestId;
        deadlineNanos = dependencies.nanoTime.getAsLong() + TIMEOUT_NANOS;
        tooltipState = TooltipState.FETCHING;
        boolean sent = dependencies.transport.send(
                new UnknownStackDetailsRequestPayload(requestId, reportId));
        if (!sent) {
            clearPending();
            tooltipState = TooltipState.ERROR;
        }
        return sent;
    }

    void tick() {
        expireIfNeeded();
    }

    String tooltip() {
        expireIfNeeded();
        return PerfDisplayText.translated(tooltipState.translationKey);
    }

    long pendingRequestId() {
        return pendingRequestId;
    }

    private void accept(UnknownStackDetailsPayload payload) {
        expireIfNeeded();
        if (!open || payload.reportId() != reportId
                || payload.requestId() != pendingRequestId) {
            return;
        }
        clearPending();
        switch (payload.status()) {
            case OK -> copy(payload);
            case STALE -> tooltipState = TooltipState.STALE;
            case EMPTY -> tooltipState = TooltipState.EMPTY;
            case DENIED -> tooltipState = TooltipState.DENIED;
            case BUSY -> tooltipState = TooltipState.BUSY;
            case ERROR -> tooltipState = TooltipState.ERROR;
        }
    }

    private void copy(UnknownStackDetailsPayload payload) {
        dependencies.output.copy(format(payload, unknownSummary));
        dependencies.output.notifyCopied(payload.average().size());
        tooltipState = TooltipState.COPIED;
        if (!profiling) {
            recentStopped = payload;
        }
    }

    private void expireIfNeeded() {
        if (pendingRequestId != 0L
                && dependencies.nanoTime.getAsLong() >= deadlineNanos) {
            clearPending();
            tooltipState = TooltipState.TIMEOUT;
        }
    }

    private void clearPending() {
        pendingRequestId = 0L;
        deadlineNanos = 0L;
    }

    private static void clearMismatchedCache(long currentReportId) {
        if (recentStopped != null && recentStopped.reportId() != currentReportId) {
            recentStopped = null;
        }
    }

    static String format(UnknownStackDetailsPayload payload) {
        return format(payload, null);
    }

    static String format(UnknownStackDetailsPayload payload, UnknownSummary summary) {
        StringBuilder text = new StringBuilder();
        text.append("ServerWarashi Unknown stack details\n")
                .append("Report ID: ").append(payload.reportId()).append('\n');
        appendCoverage(text, payload, summary);
        text.append("Reservoir samples: ").append(payload.retainedCount()).append('\n');
        double estimateScale = summary == null ? 1.0D : summary.estimateScale();
        appendSection(text, payload.average(), payload.retainedCount(), estimateScale);
        return text.toString();
    }

    private static void appendCoverage(StringBuilder text,
                                       UnknownStackDetailsPayload payload,
                                       UnknownSummary summary) {
        if (summary == null || summary.samples == 0L) {
            text.append("Reliable JFR observations: ")
                    .append(payload.totalObserved()).append('\n');
            return;
        }
        text.append("Primary Unknown: ")
                .append(String.format(Locale.ROOT, "%,d samples | %.3f MSPT%n",
                        summary.samples, summary.mspt))
                .append("Reliable JFR observations: ")
                .append(String.format(Locale.ROOT, "%,d%n", payload.totalObserved()));
    }

    private static void appendSection(StringBuilder text,
                                      List<UnknownStackDetailsPayload.Cluster> clusters,
                                      int retainedCount,
                                      double estimateScale) {
        text.append("\nAverage-time Top 3\n");
        if (clusters.isEmpty()) {
            text.append("(none)\n");
            return;
        }
        for (int index = 0; index < clusters.size(); index++) {
            UnknownStackDetailsPayload.Cluster cluster = clusters.get(index);
            double share = retainedCount == 0 ? 0.0D
                    : cluster.sampleCount() * 100.0D / retainedCount;
            text.append(index + 1).append(". ")
                    .append(String.format(Locale.ROOT, "%.2f%%", share)).append(" | ")
                    .append(String.format(Locale.ROOT, "estimated %.3f MSPT",
                            cluster.estimatedAverageMillis() * estimateScale))
                    .append("\n   Anchor: ").append(cluster.anchor())
                    .append("\n   Samples: ").append(cluster.sampleCount())
                    .append("; representative path: ")
                    .append(cluster.representativeCount()).append('\n');
            for (String frame : cluster.frames()) {
                text.append("\tat ").append(frame).append('\n');
            }
        }
    }

    record Dependencies(Transport transport, Output output, LongSupplier nanoTime) {
    }

    record UnknownSummary(long samples,
                          double mspt,
                          long tickCount,
                          int intervalMicros) {
        static UnknownSummary from(PerfReportData report) {
            long samples = 0L;
            double mspt = 0.0D;
            for (PerfReportData.TickableEntry entry : report.tickables()) {
                if (entry.type() == TickType.TASK && entry.eventLoop().isEmpty()
                        && "Unknown".equals(entry.className())) {
                    samples += entry.metric().samples();
                    mspt += entry.metric().mspt();
                }
            }
            return new UnknownSummary(samples, mspt, report.tickCount(), report.intervalMicros());
        }

        double estimateScale() {
            if (samples == 0L || tickCount == 0L) {
                return 1.0D;
            }
            double baselineMspt = samples * intervalMicros / 1_000.0D / tickCount;
            return baselineMspt <= 0.0D ? 1.0D : mspt / baselineMspt;
        }
    }

    interface Transport {
        boolean available();

        boolean send(UnknownStackDetailsRequestPayload request);
    }

    interface Output {
        void copy(String text);

        void notifyCopied(int count);
    }

    private enum TooltipState {
        READY("screen.serverwarashi.profiler.unknown.click"),
        FETCHING("screen.serverwarashi.profiler.unknown.fetching"),
        COPIED("screen.serverwarashi.profiler.unknown.copied"),
        TIMEOUT("screen.serverwarashi.profiler.unknown.timeout"),
        STALE("screen.serverwarashi.profiler.unknown.stale"),
        EMPTY("screen.serverwarashi.profiler.unknown.empty"),
        DENIED("screen.serverwarashi.profiler.unknown.denied"),
        BUSY("screen.serverwarashi.profiler.unknown.busy"),
        ERROR("screen.serverwarashi.profiler.unknown.error");

        private final String translationKey;

        TooltipState(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private static final class ProductionTransport implements Transport {
        @Override
        public boolean available() {
            return UnknownStackDetailsClient.canSend();
        }

        @Override
        public boolean send(UnknownStackDetailsRequestPayload request) {
            return UnknownStackDetailsClient.send(request);
        }
    }

    private static final class ProductionOutput implements Output {
        @Override
        public void copy(String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }

        @Override
        public void notifyCopied(int count) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.serverwarashi.unknown.copied", count), true);
            }
        }
    }
}
