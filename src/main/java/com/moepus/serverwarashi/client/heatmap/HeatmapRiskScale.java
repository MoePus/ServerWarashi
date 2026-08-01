package com.moepus.serverwarashi.client.heatmap;

import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;

final class HeatmapRiskScale {
    static final HeatmapColorStop GREEN = HeatmapColorStop.rgb(64, 220, 120);
    static final HeatmapColorStop YELLOW = HeatmapColorStop.rgb(255, 214, 64);
    static final HeatmapColorStop ORANGE = HeatmapColorStop.rgb(255, 145, 32);
    static final HeatmapColorStop RED = HeatmapColorStop.rgb(244, 67, 54);

    private static final double LOG_TWO = Math.log(2.0D);
    private static final HeatmapColorStop PURPLE = HeatmapColorStop.rgb(190, 45, 220);
    private static final HeatmapColorStop[] HEATMAP_COLORS = {
            YELLOW.withAlpha(96),
            ORANGE.withAlpha(112),
            RED.withAlpha(136),
            PURPLE.withAlpha(160)
    };
    static final double RED_CONTRIBUTION = contributionAtPosition(
            weakPosition(WeakChunkSnapshot.ENTITY_RED_THRESHOLD));
    static final double PURPLE_CONTRIBUTION = contributionAtPosition(
            weakPosition(WeakChunkSnapshot.ENTITY_PURPLE_THRESHOLD));

    private HeatmapRiskScale() {
    }

    static int colorForContribution(double contribution) {
        return colorForPosition(Math.log(Math.max(1.0D, contribution)) / LOG_TWO);
    }

    static int colorForPosition(double position) {
        return HeatmapColorStop.interpolate(HEATMAP_COLORS, position);
    }

    static double contributionAtPosition(double position) {
        return Math.pow(2.0D, position);
    }

    static double weakPosition(int entityCount) {
        double span = WeakChunkSnapshot.ENTITY_PURPLE_THRESHOLD
                - WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD;
        return Math.max(0.0D, Math.min(HEATMAP_COLORS.length - 1.0D,
                (entityCount - WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD) / span
                        * (HEATMAP_COLORS.length - 1)));
    }

}
