package com.moepus.serverwarashi.testsupport;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.List;

public final class PerfReportFixtures {
    private PerfReportFixtures() {
    }

    public static ReportBuilder reportBuilder() {
        return new ReportBuilder();
    }

    public static PerfReportData.Metric metric(double mspt, double max) {
        return new PerfReportData.Metric(mspt, max, 1L);
    }

    public static PerfReportData.Metric metric(double mspt, double max, long samples) {
        return new PerfReportData.Metric(mspt, max, samples);
    }

    public static final class ReportBuilder {
        private long reportId;
        private double serverMspt;
        private long elapsedMillis;
        private long generatedAtEpochMillis;
        private long tickCount;
        private long totalSamples;
        private int intervalMicros = 1_000;
        private PerfReportData.SamplingMetadata sampling =
                PerfReportData.SamplingMetadata.notStarted();
        private int omittedChunks;
        private int omittedTickables;
        private int omittedOwnerSources;
        private int omittedLoaderSources;
        private List<PerfReportData.ChunkEntry> chunks = List.of();
        private List<PerfReportData.TickableEntry> tickables = List.of();
        private List<PerfReportData.OwnerEntry> owners = List.of();
        private List<PerfReportData.LoaderSourceEntry> loaderSources = List.of();

        public ReportBuilder reportId(long value) {
            reportId = value;
            return this;
        }

        public ReportBuilder serverTiming(double mspt, long elapsed, long generatedAt) {
            serverMspt = mspt;
            elapsedMillis = elapsed;
            generatedAtEpochMillis = generatedAt;
            return this;
        }

        public ReportBuilder sampleWindow(long ticks, long samples, int interval) {
            tickCount = ticks;
            totalSamples = samples;
            intervalMicros = interval;
            return this;
        }

        public ReportBuilder sampling(PerfReportData.SamplingMetadata value) {
            sampling = value;
            return this;
        }

        public ReportBuilder omissions(int chunkCount, int tickableCount, int ownerCount) {
            omittedChunks = chunkCount;
            omittedTickables = tickableCount;
            omittedOwnerSources = ownerCount;
            return this;
        }

        public ReportBuilder omittedLoaderSources(int value) {
            omittedLoaderSources = value;
            return this;
        }

        public ReportBuilder chunks(List<PerfReportData.ChunkEntry> value) {
            chunks = value;
            return this;
        }

        public ReportBuilder tickables(List<PerfReportData.TickableEntry> value) {
            tickables = value;
            return this;
        }

        public ReportBuilder owners(List<PerfReportData.OwnerEntry> value) {
            owners = value;
            return this;
        }

        public ReportBuilder loaderSources(List<PerfReportData.LoaderSourceEntry> value) {
            loaderSources = value;
            return this;
        }

        public PerfReportData build() {
            return new PerfReportData(reportId, serverMspt, elapsedMillis,
                    generatedAtEpochMillis, tickCount, totalSamples, intervalMicros, sampling,
                    omittedChunks, omittedTickables, omittedOwnerSources, omittedLoaderSources,
                    chunks, tickables, owners, loaderSources);
        }
    }
}
