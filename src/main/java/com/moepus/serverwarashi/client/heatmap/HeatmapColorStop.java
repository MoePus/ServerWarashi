package com.moepus.serverwarashi.client.heatmap;

record HeatmapColorStop(int alpha, int red, int green, int blue) {
    int interpolate(HeatmapColorStop other, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        return pack(lerp(alpha, other.alpha, clamped), lerp(red, other.red, clamped),
                lerp(green, other.green, clamped), lerp(blue, other.blue, clamped));
    }

    private static int lerp(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    private static int pack(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
