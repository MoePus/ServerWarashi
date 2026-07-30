package com.moepus.serverwarashi.common.modules.weakload;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class StableEntitySampler {
    static final int SAMPLE_LIMIT = 16;
    private static final long SEED_SALT = 0x6A09E667F3BCC909L;
    private static final long WEYL_INCREMENT = 0x9E3779B97F4A7C15L;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private StableEntitySampler() {
    }

    static long seed(String dimension, long chunkPos, int entityCount) {
        long value = SEED_SALT ^ stableHash(dimension) ^ mix64(chunkPos);
        return mix64(value ^ Integer.toUnsignedLong(entityCount));
    }

    static long sweepStart(String dimension, long sweepId) {
        long weylValue = sweepId * WEYL_INCREMENT;
        return mix64(SEED_SALT ^ stableHash(dimension) ^ weylValue);
    }

    static int[] sampleIndices(int population, int requested, long seed) {
        if (population < 0 || requested < 0) {
            throw new IllegalArgumentException("Population and sample size must be non-negative");
        }
        int sampleSize = Math.min(population, requested);
        IntOpenHashSet selected = new IntOpenHashSet(sampleSize);
        SplitMix64 random = new SplitMix64(seed);
        for (int end = population - sampleSize; end < population; end++) {
            int candidate = random.nextInt(end + 1);
            selected.add(selected.contains(candidate) ? end : candidate);
        }
        int[] result = selected.toIntArray();
        java.util.Arrays.sort(result);
        return result;
    }

    static <T extends Comparable<? super T>> Map<T, Integer> estimateCounts(Map<T, Integer> samples,
                                                                            int sampleSize,
                                                                            int population) {
        validateSamples(samples, sampleSize, population);
        List<Estimate<T>> estimates = new ArrayList<>(samples.size());
        int assigned = 0;
        for (var entry : samples.entrySet()) {
            long scaled = (long) entry.getValue() * population;
            int base = (int) (scaled / sampleSize);
            estimates.add(new Estimate<>(entry.getKey(), base, scaled % sampleSize));
            assigned += base;
        }
        estimates.sort(Comparator.<Estimate<T>>comparingLong(Estimate::remainder).reversed()
                .thenComparing(Estimate::type));
        for (int index = 0; index < population - assigned; index++) {
            estimates.get(index).count++;
        }
        TreeMap<T, Integer> result = new TreeMap<>();
        estimates.forEach(estimate -> result.put(estimate.type, estimate.count));
        return Map.copyOf(result);
    }

    static <T> List<T> sampleValues(List<? extends List<T>> sections, int[] sortedIndices) {
        List<T> result = new ArrayList<>(sortedIndices.length);
        int sectionIndex = 0;
        int sectionStart = 0;
        List<T> values = sections.get(sectionIndex);
        for (int index : sortedIndices) {
            while (index >= sectionStart + values.size()) {
                sectionStart += values.size();
                values = sections.get(++sectionIndex);
            }
            result.add(values.get(index - sectionStart));
        }
        return List.copyOf(result);
    }

    private static void validateSamples(Map<?, Integer> samples, int sampleSize, int population) {
        if (samples.isEmpty() || sampleSize <= 0 || population < sampleSize) {
            throw new IllegalArgumentException("Invalid entity sample");
        }
        int observed = samples.values().stream().mapToInt(Integer::intValue).sum();
        if (observed != sampleSize || samples.values().stream().anyMatch(value -> value <= 0)) {
            throw new IllegalArgumentException("Entity sample counts must sum to sample size");
        }
    }

    private static long stableHash(String value) {
        long hash = FNV_OFFSET;
        for (byte character : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ Byte.toUnsignedInt(character)) * FNV_PRIME;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static final class Estimate<T extends Comparable<? super T>> {
        final T type;
        int count;
        final long remainder;

        Estimate(T type, int count, long remainder) {
            this.type = type;
            this.count = count;
            this.remainder = remainder;
        }

        T type() {
            return type;
        }

        long remainder() {
            return remainder;
        }
    }

    private static final class SplitMix64 {
        private long state;

        SplitMix64(long seed) {
            state = seed;
        }

        int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive");
            }
            long value = nextLong() >>> 1;
            long remainder = value % bound;
            while (value - remainder + bound - 1L < 0L) {
                value = nextLong() >>> 1;
                remainder = value % bound;
            }
            return (int) remainder;
        }

        private long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return mix64(state);
        }
    }
}
