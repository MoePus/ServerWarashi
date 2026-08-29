package com.moepus.serverwarashi.client.world;

import com.moepus.serverwarashi.client.config.ClientConfig;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;

public final class WorldOverlayMath {
    public static final double OBJECT_FADE_START = 80.0D;
    public static final double OBJECT_FADE_END = 96.0D;
    public static final double BEAM_FADE_START = 64.0D;
    public static final double BEAM_FADE_END = 128.0D;
    public static final double LABEL_FIXED_DISTANCE = 8.0D;
    public static final double LABEL_TRANSITION_END = 12.0D;
    public static final double LABEL_FADE_START = 20.0D;
    public static final double LABEL_FADE_END = 40.0D;
    public static final double MAX_FLAME_VIEWPORT_FRACTION = 0.18D;
    public static final int MAX_DISPLAY_MICROS = 999_999_999;
    public static final int OVERFLOW_MICROS = MAX_DISPLAY_MICROS + 1;
    public static final int FLAME_STRENGTH_SCALE = 1_024;
    public static final double MAX_PACKED_FLAME_STRENGTH = 32_767.0D / FLAME_STRENGTH_SCALE;
    private static final int WORD_MASK = 0x7FFF;
    public static final int LABEL_WORD_BASE = WORD_MASK + 1;
    private static final int[] COLORS = {
            0x40DC78, 0xFFD640, 0xFF9120, 0xF44336, 0xBE2DDC
    };

    private WorldOverlayMath() {
    }

    public static boolean shouldRender(double effectiveSamples,
                                       ClientConfig.WorldOverlayFilter filter) {
        return effectiveSamples > 0.0D && (filter == ClientConfig.WorldOverlayFilter.ALL
                || effectiveSamples >= 2.0D);
    }

    public static int colorRgb(double effectiveSamples) {
        double position = clamp(effectiveSamples, 1.0D, 5.0D) - 1.0D;
        int lower = Math.min((int) Math.floor(position), COLORS.length - 1);
        int upper = Math.min(lower + 1, COLORS.length - 1);
        double amount = position - lower;
        return lerpColor(COLORS[lower], COLORS[upper], amount);
    }

    public static int roundedMicros(PerfReportData.Metric metric) {
        if (metric == null || !Double.isFinite(metric.mspt()) || metric.mspt() <= 0.0D) {
            return 0;
        }
        double micros = metric.mspt() * 1_000.0D;
        return micros > MAX_DISPLAY_MICROS ? OVERFLOW_MICROS : (int) Math.round(micros);
    }

    public static PackedLabel packLabel(int micros) {
        int value = Math.max(0, Math.min(OVERFLOW_MICROS, micros));
        return new PackedLabel(value & WORD_MASK, -(value / LABEL_WORD_BASE) - 1);
    }

    public static int unpackLabel(int lowWord, int markedHighWord) {
        if (markedHighWord >= 0) {
            throw new IllegalArgumentException("Label high word must be negative");
        }
        return lowWord + (-markedHighWord - 1) * LABEL_WORD_BASE;
    }

    public static int packFlameStrength(double effectiveSamples) {
        if (!Double.isFinite(effectiveSamples) || effectiveSamples <= 0.0D) {
            return 0;
        }
        return (int) Math.round(clamp(effectiveSamples * FLAME_STRENGTH_SCALE, 1.0D, 32_767.0D));
    }

    public static double unpackFlameStrength(int packed) {
        return clamp(packed, 0, 32_767) / (double) FLAME_STRENGTH_SCALE;
    }

    public static int packLabelStrength(double effectiveSamples) {
        double normalized = clamp(effectiveSamples / MAX_PACKED_FLAME_STRENGTH, 0.0D, 1.0D);
        return (int) Math.round(normalized * 255.0D);
    }

    private static int lerpColor(int from, int to, double amount) {
        int red = lerpByte(from >> 16, to >> 16, amount);
        int green = lerpByte(from >> 8, to >> 8, amount);
        int blue = lerpByte(from, to, amount);
        return red << 16 | green << 8 | blue;
    }

    private static int lerpByte(int from, int to, double amount) {
        return (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record PackedLabel(int lowWord, int markedHighWord) {
    }
}
