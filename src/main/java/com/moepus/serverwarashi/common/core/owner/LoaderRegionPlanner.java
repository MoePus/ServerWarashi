package com.moepus.serverwarashi.common.core.owner;

import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class LoaderRegionPlanner {
    private LoaderRegionPlanner() {
    }

    static PerfAttributionSnapshot.LoaderPlan plan(Map<SourceKey, Set<Long>> chunksBySource,
                                                   Options options) {
        List<SourceKey> keys = chunksBySource.keySet().stream()
                .sorted(SourceKey.ORDER)
                .limit(options.sourceLimit)
                .toList();
        List<SourceWork> work = keys.stream()
                .map(key -> new SourceWork(key, compress(chunksBySource.get(key), options.maxRegionSpan)))
                .toList();
        allocateFairly(work, options.regionLimit);
        List<PerfAttributionSnapshot.LoaderSource> sources = work.stream()
                .filter(SourceWork::selected)
                .map(SourceWork::build)
                .toList();
        return new PerfAttributionSnapshot.LoaderPlan(
                sources, Math.max(0, chunksBySource.size() - sources.size()));
    }

    private static void allocateFairly(List<SourceWork> work, int regionLimit) {
        int remaining = regionLimit;
        boolean progressed = true;
        while (remaining > 0 && progressed) {
            progressed = false;
            for (SourceWork source : work) {
                if (remaining == 0) {
                    return;
                }
                if (source.selectNext()) {
                    remaining--;
                    progressed = true;
                }
            }
        }
    }

    private static List<PerfAttributionSnapshot.LoaderRegion> compress(Set<Long> chunks, int maxSpan) {
        Map<Integer, List<Integer>> rows = rows(chunks);
        Map<Span, MutableRegion> active = new LinkedHashMap<>();
        List<MutableRegion> complete = new ArrayList<>();
        for (var row : rows.entrySet()) {
            Map<Span, MutableRegion> next = new LinkedHashMap<>();
            for (Span span : spans(row.getValue(), maxSpan)) {
                MutableRegion region = active.remove(span);
                if (region != null && region.canExtend(row.getKey(), maxSpan)) {
                    next.put(span, region.extend());
                } else {
                    if (region != null) {
                        complete.add(region);
                    }
                    next.put(span, new MutableRegion(span, row.getKey()));
                }
            }
            complete.addAll(active.values());
            active = next;
        }
        complete.addAll(active.values());
        return complete.stream().map(MutableRegion::build)
                .sorted(Comparator.comparingInt(PerfAttributionSnapshot.LoaderRegion::minChunkZ)
                        .thenComparingInt(PerfAttributionSnapshot.LoaderRegion::minChunkX))
                .toList();
    }

    private static Map<Integer, List<Integer>> rows(Set<Long> chunks) {
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        chunks.forEach(packed -> rows.computeIfAbsent(ChunkPos.getZ(packed), ignored -> new ArrayList<>())
                .add(ChunkPos.getX(packed)));
        rows.values().forEach(row -> row.sort(Integer::compareTo));
        return rows;
    }

    private static List<Span> spans(List<Integer> chunkXs, int maxSpan) {
        List<Span> result = new ArrayList<>();
        int start = chunkXs.getFirst();
        int previous = start;
        for (int index = 1; index < chunkXs.size(); index++) {
            int current = chunkXs.get(index);
            if (current != previous + 1) {
                appendSplitSpan(result, start, previous, maxSpan);
                start = current;
            }
            previous = current;
        }
        appendSplitSpan(result, start, previous, maxSpan);
        return result;
    }

    private static void appendSplitSpan(List<Span> output, int start, int end, int maxSpan) {
        for (long current = start; current <= end; current += maxSpan) {
            output.add(new Span((int) current, (int) Math.min(maxSpan, (long) end - current + 1L)));
        }
    }

    record Options(int sourceLimit, int regionLimit, int maxRegionSpan) {
        Options {
            if (sourceLimit < 0 || regionLimit < 0 || maxRegionSpan <= 0) {
                throw new IllegalArgumentException("Invalid loader region planning limits");
            }
        }
    }

    record SourceKey(String dimension, String sourceId, String loaderName,
                     PerfAttributionSnapshot.Position position,
                     BlockLoaderRef loaderRef) {
        private static final Comparator<SourceKey> ORDER = Comparator
                .comparingLong((SourceKey key) -> Integer.toUnsignedLong(key.sourceId.hashCode()))
                .thenComparing(SourceKey::dimension)
                .thenComparing(SourceKey::sourceId);
    }

    private static final class SourceWork {
        private final SourceKey key;
        private final List<PerfAttributionSnapshot.LoaderRegion> regions;
        private int selectedCount;

        private SourceWork(SourceKey key, List<PerfAttributionSnapshot.LoaderRegion> regions) {
            this.key = key;
            this.regions = regions;
        }

        boolean selectNext() {
            if (selectedCount == regions.size()) {
                return false;
            }
            selectedCount++;
            return true;
        }

        boolean selected() {
            return selectedCount > 0;
        }

        PerfAttributionSnapshot.LoaderSource build() {
            return new PerfAttributionSnapshot.LoaderSource(key.dimension, key.sourceId, key.loaderName,
                    key.position, regions.size() - selectedCount, regions.subList(0, selectedCount),
                    key.loaderRef, null);
        }
    }

    private record Span(int minX, int width) {
    }

    private static final class MutableRegion {
        private final Span span;
        private final int minZ;
        private int height = 1;

        private MutableRegion(Span span, int minZ) {
            this.span = span;
            this.minZ = minZ;
        }

        MutableRegion extend() {
            height++;
            return this;
        }

        boolean canExtend(int chunkZ, int maxSpan) {
            return minZ + height == chunkZ && height < maxSpan;
        }

        PerfAttributionSnapshot.LoaderRegion build() {
            return new PerfAttributionSnapshot.LoaderRegion(span.minX, minZ, span.width, height);
        }
    }
}
