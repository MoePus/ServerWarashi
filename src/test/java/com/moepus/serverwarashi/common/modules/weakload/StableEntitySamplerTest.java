package com.moepus.serverwarashi.common.modules.weakload;

import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableEntitySamplerTest {
    @Test
    void producesStableUniqueIndices() {
        long seed = StableEntitySampler.seed("minecraft:overworld", 123L, 5_000);
        int[] first = StableEntitySampler.sampleIndices(5_000, 16, seed);
        int[] second = StableEntitySampler.sampleIndices(5_000, 16, seed);

        assertArrayEquals(first, second);
        assertEquals(16, first.length);
        for (int index = 0; index < first.length; index++) {
            assertTrue(first[index] >= 0 && first[index] < 5_000);
            if (index > 0) {
                assertTrue(first[index - 1] < first[index]);
            }
        }
        assertFalse(java.util.Arrays.equals(first, StableEntitySampler.sampleIndices(5_000, 16,
                StableEntitySampler.seed("minecraft:the_nether", 123L, 5_000))));
    }

    @Test
    void mapsGlobalIndicesUsingOnlySizeAndGet() {
        List<String> first = new RandomAccessOnlyList<>(List.of("a", "b"));
        List<String> second = new RandomAccessOnlyList<>(List.of("c", "d", "e"));

        assertEquals(List.of("a", "c", "e"),
                StableEntitySampler.sampleValues(List.of(first, second), new int[]{0, 2, 4}));
    }

    @Test
    void distributesRemaindersDeterministicallyAndPreservesTotal() {
        Map<String, Integer> estimates = StableEntitySampler.estimateCounts(
                Map.of("test:a", 5, "test:b", 5, "test:c", 6), 16, 50);

        assertEquals(Map.of("test:a", 16, "test:b", 15, "test:c", 19), estimates);
        assertEquals(50, estimates.values().stream().mapToInt(Integer::intValue).sum());
        assertThrows(IllegalArgumentException.class,
                () -> StableEntitySampler.estimateCounts(Map.of("test:a", 1), 16, 50));
    }

    private static final class RandomAccessOnlyList<T> extends AbstractList<T> {
        private final List<T> values;

        private RandomAccessOnlyList(List<T> values) {
            this.values = values;
        }

        @Override
        public T get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Iterator<T> iterator() {
            throw new AssertionError("Entity list must not be iterated");
        }
    }
}
