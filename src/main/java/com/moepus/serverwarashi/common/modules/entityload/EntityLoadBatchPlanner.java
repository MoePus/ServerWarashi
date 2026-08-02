package com.moepus.serverwarashi.common.modules.entityload;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EntityLoadBatchPlanner {
    private EntityLoadBatchPlanner() {
    }

    static <K extends Comparable<? super K>> Plan<K> plan(
            List<K> types, int batchThreshold, int topLimit, int retainedPerType) {
        if (types.size() <= batchThreshold) {
            return Plan.empty();
        }

        Map<K, Integer> counts = new HashMap<>();
        types.forEach(type -> counts.merge(type, 1, Integer::sum));
        List<K> targets = counts.entrySet().stream()
                .sorted((left, right) -> compareCounts(left, right))
                .limit(topLimit)
                .filter(entry -> entry.getValue() > retainedPerType)
                .map(Map.Entry::getKey)
                .toList();
        if (targets.isEmpty()) {
            return Plan.empty();
        }

        Map<K, Integer> retained = new HashMap<>();
        Map<K, Integer> removed = new HashMap<>();
        BitSet removalIndexes = new BitSet(types.size());
        for (int index = 0; index < types.size(); index++) {
            K type = types.get(index);
            if (!targets.contains(type)) {
                continue;
            }
            int retainedCount = retained.merge(type, 1, Integer::sum);
            if (retainedCount > retainedPerType) {
                removalIndexes.set(index);
                removed.merge(type, 1, Integer::sum);
            }
        }

        List<Removal<K>> removals = new ArrayList<>(targets.size());
        targets.forEach(type -> removals.add(new Removal<>(type, removed.getOrDefault(type, 0))));
        return new Plan<>(removalIndexes, removals);
    }

    private static <K extends Comparable<? super K>> int compareCounts(
            Map.Entry<K, Integer> left, Map.Entry<K, Integer> right) {
        int countComparison = Integer.compare(right.getValue(), left.getValue());
        return countComparison != 0 ? countComparison : left.getKey().compareTo(right.getKey());
    }

    record Removal<K>(K type, int count) {
    }

    static final class Plan<K> {
        private final BitSet removalIndexes;
        private final List<Removal<K>> removals;

        private Plan(BitSet removalIndexes, List<Removal<K>> removals) {
            this.removalIndexes = (BitSet) removalIndexes.clone();
            this.removals = List.copyOf(removals);
        }

        private static <K> Plan<K> empty() {
            return new Plan<>(new BitSet(), List.of());
        }

        boolean removes(int index) {
            return removalIndexes.get(index);
        }

        int removalCount() {
            return removalIndexes.cardinality();
        }

        List<Removal<K>> removals() {
            return removals;
        }
    }
}
