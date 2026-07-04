package com.moepus.serverwarashi.common.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class TopN {
    private TopN() {
    }

    public static <T> List<T> select(Collection<T> items, int limit, Comparator<? super T> comparator) {
        if (limit <= 0 || items.isEmpty()) {
            return List.of();
        }
        int initialCapacity = Math.min(limit, items.size());
        PriorityQueue<T> heap = new PriorityQueue<>(initialCapacity, comparator.reversed());
        for (T item : items) {
            if (heap.size() < limit) {
                heap.add(item);
            } else if (comparator.compare(item, heap.peek()) < 0) {
                heap.poll();
                heap.add(item);
            }
        }
        List<T> result = new ArrayList<>(heap);
        result.sort(comparator);
        return result;
    }
}
