package com.moepus.serverwarashi.common.modules.performance.sampling;

import java.util.Arrays;
import java.util.random.RandomGenerator;

final class FloatReservoir {
    private static final int CAPACITY = 256;
    private float[] values = new float[0];
    private long seen;
    private int size;

    void add(float value, RandomGenerator random) {
        seen++;
        if (size < CAPACITY) {
            ensureCapacity(size + 1);
            values[size++] = value;
            return;
        }
        long replacement = random.nextLong(seen);
        if (replacement < CAPACITY) {
            values[(int) replacement] = value;
        }
    }

    private void ensureCapacity(int required) {
        if (required <= values.length) {
            return;
        }
        int capacity = Math.min(CAPACITY, Math.max(8, values.length * 2));
        values = Arrays.copyOf(values, capacity);
    }

    double percentile(double quantile) {
        if (size == 0) {
            return 0.0D;
        }
        float[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(quantile * size) - 1;
        return sorted[Math.clamp(index, 0, size - 1)];
    }
}
