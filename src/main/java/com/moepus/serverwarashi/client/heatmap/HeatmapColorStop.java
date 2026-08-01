package com.moepus.serverwarashi.client.heatmap;

record HeatmapColorStop(int alpha, int red, int green, int blue) {
    static HeatmapColorStop rgb(int red, int green, int blue) {
        return new HeatmapColorStop(255, red, green, blue);
    }

    HeatmapColorStop withAlpha(int alpha) {
        return new HeatmapColorStop(alpha, red, green, blue);
    }

    int interpolate(HeatmapColorStop other, double amount) {
        double clamped = Math.max(0.0D, Math.min(1.0D, amount));
        return pack(lerp(alpha, other.alpha, clamped), lerp(red, other.red, clamped),
                lerp(green, other.green, clamped), lerp(blue, other.blue, clamped));
    }

    static int interpolate(HeatmapColorStop[] stops, double position) {
        double clamped = Math.max(0.0D, Math.min(stops.length - 1.0D, position));
        int lower = Math.min((int) Math.floor(clamped), stops.length - 1);
        int upper = Math.min(lower + 1, stops.length - 1);
        return stops[lower].interpolate(stops[upper], clamped - lower);
    }

    private static int lerp(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    private static int pack(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
