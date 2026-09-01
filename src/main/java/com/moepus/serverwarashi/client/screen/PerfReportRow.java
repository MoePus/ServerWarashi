package com.moepus.serverwarashi.client.screen;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

import java.util.Objects;
import java.util.Optional;

record PerfReportRow(String key, Content content, Display display) {
    private static final PerfReportData.Metric EMPTY_METRIC =
            new PerfReportData.Metric(0.0D, 0.0D, 0L);

    PerfReportRow {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(display, "display");
    }

    static PerfReportRow metric(String key, Content content, MetricOptions options) {
        return new PerfReportRow(key, content, new Display(
                options.auxiliary(), new MetricValue(options.metric()), options.presentation()));
    }

    static PerfReportRow summary(String key, Content content, SummaryOptions options) {
        return new PerfReportRow(key, content, new Display(
                options.auxiliary(), NoMetric.INSTANCE, options.presentation()));
    }

    String label() {
        return content.labelPrefix() + content.coordinate();
    }

    AuxiliaryCells auxiliary() {
        return display.auxiliary();
    }

    PerfReportData.Metric metric() {
        return display.metric().value();
    }

    boolean showsMetric() {
        return display.metric() instanceof MetricValue;
    }

    Presentation presentation() {
        return display.presentation();
    }

    Capabilities capabilities() {
        return presentation().capabilities();
    }

    record Content(String labelPrefix, String coordinate) {
        Content {
            Objects.requireNonNull(labelPrefix, "labelPrefix");
            Objects.requireNonNull(coordinate, "coordinate");
        }
    }

    record Display(AuxiliaryCells auxiliary,
                   MetricContent metric,
                   Presentation presentation) {
    }

    record MetricOptions(AuxiliaryCells auxiliary,
                         PerfReportData.Metric metric,
                         Presentation presentation) {
    }

    record SummaryOptions(AuxiliaryCells auxiliary, Presentation presentation) {
    }

    record Presentation(int depth, Capabilities capabilities) {
        Presentation {
            Objects.requireNonNull(capabilities, "capabilities");
        }
    }

    sealed interface AuxiliaryCells permits ChunkCells, TickableCells, OwnerCells {
        default int entityCount() {
            return 0;
        }

        default int blockEntityCount() {
            return 0;
        }

        default int chunkCount() {
            return 0;
        }

        default String type() {
            return "";
        }

        default Optional<PlayerStatus> playerStatus() {
            return Optional.empty();
        }
    }

    record ChunkCells(int entityCount, int blockEntityCount) implements AuxiliaryCells {
    }

    record TickableCells(String type) implements AuxiliaryCells {
    }

    record OwnerCells(int chunkCount,
                      Optional<PlayerStatus> playerStatus) implements AuxiliaryCells {
        OwnerCells {
            Objects.requireNonNull(playerStatus, "playerStatus");
        }
    }

    record PlayerStatus(boolean online, long lastLoginEpochMillis) {
    }

    sealed interface MetricContent permits MetricValue, NoMetric {
        PerfReportData.Metric value();
    }

    record MetricValue(PerfReportData.Metric value) implements MetricContent {
        MetricValue {
            Objects.requireNonNull(value, "value");
        }
    }

    enum NoMetric implements MetricContent {
        INSTANCE;

        @Override
        public PerfReportData.Metric value() {
            return EMPTY_METRIC;
        }
    }

    record Capabilities(Optional<Expansion> expansion,
                        Optional<CoordinateAction> coordinateAction,
                        Optional<LabelTooltip> labelTooltip) {
        private static final Capabilities NONE = new Capabilities(
                Optional.empty(), Optional.empty(), Optional.empty());

        Capabilities {
            Objects.requireNonNull(expansion, "expansion");
            Objects.requireNonNull(coordinateAction, "coordinateAction");
            Objects.requireNonNull(labelTooltip, "labelTooltip");
        }

        static Capabilities none() {
            return NONE;
        }

        Capabilities withExpansion(String key, boolean expanded) {
            return new Capabilities(Optional.of(new Expansion(key, expanded)),
                    coordinateAction, labelTooltip);
        }

        Capabilities withCoordinateAction(String command) {
            return command == null ? this : new Capabilities(expansion,
                    Optional.of(new CoordinateAction(command)), labelTooltip);
        }

        Capabilities withLabelTooltip(String text) {
            return text == null ? this : new Capabilities(expansion,
                    coordinateAction, Optional.of(new LabelTooltip(text)));
        }
    }

    record Expansion(String key, boolean expanded) {
    }

    record CoordinateAction(String command) {
    }

    record LabelTooltip(String text) {
    }
}
