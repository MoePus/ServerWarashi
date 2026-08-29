package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PerfWorldHotspotStream {
    private final RegistryIdResolver registryIds;
    private PerfWorldHotspotIndex previous;
    private PerfWorldHotspotIndex current;

    PerfWorldHotspotStream() {
        this(PerfWorldHotspotStream::builtInRegistryId);
    }

    PerfWorldHotspotStream(RegistryIdResolver registryIds) {
        this.registryIds = registryIds;
    }

    boolean update(PerfWorldHotspotIndex next) {
        if (next == current || current != null && next != null
                && next.streamEpoch() == current.streamEpoch()
                && next.revision() == current.revision()) {
            return false;
        }
        previous = current;
        current = next;
        return true;
    }

    void clear() {
        previous = null;
        current = null;
    }

    boolean hasRevision(PerfWorldHotspotIndex index) {
        return index == null ? current == null : current != null
                && index.streamEpoch() == current.streamEpoch()
                && index.revision() == current.revision();
    }

    Emission emit(Cursor cursor,
                  ResourceLocation dimension,
                  double centerX,
                  double centerZ,
                  int limit) {
        var view = new PerfWorldHotspotSelector.View(
                dimension.toString(), centerX, centerZ);
        boolean delta = canDelta(cursor, view);
        List<PerfWorldHotspotPayload.Entry> selected = selectWire(current, view, limit);
        PayloadEntries entries = delta
                ? deltaEntries(selected, selectWire(previous, cursor.view(), limit))
                : new PayloadEntries(selected, List.of());
        long epoch = current == null ? 0L : current.streamEpoch();
        long revision = current == null ? 0L : current.revision();
        long tickCount = current == null ? 0L : current.tickCount();
        long nanosPerSample = current == null ? 0L : current.nanosPerSample();
        long baseRevision = delta ? previous.revision() : 0L;
        var payload = new PerfWorldHotspotPayload(epoch, baseRevision, revision,
                tickCount, nanosPerSample, dimension, !delta, entries.upserts(), entries.removals());
        return new Emission(payload, new Cursor(
                epoch, revision, dimension, centerX, centerZ));
    }

    private boolean canDelta(Cursor cursor, PerfWorldHotspotSelector.View view) {
        return cursor != null && previous != null && current != null
                && previous.streamEpoch() == current.streamEpoch()
                && cursor.streamEpoch() == current.streamEpoch()
                && cursor.revision() == previous.revision()
                && cursor.dimension().toString().equals(view.dimension());
    }

    private PayloadEntries deltaEntries(
            List<PerfWorldHotspotPayload.Entry> currentEntries,
            List<PerfWorldHotspotPayload.Entry> previousEntries) {
        Map<PerfWorldHotspotIndex.Key, PerfWorldHotspotPayload.Entry> old = wireIndex(previousEntries);
        Map<PerfWorldHotspotIndex.Key, PerfWorldHotspotPayload.Entry> next = wireIndex(currentEntries);
        List<PerfWorldHotspotPayload.Entry> upserts = new ArrayList<>();
        for (PerfWorldHotspotPayload.Entry entry : next.values()) {
            if (!entry.equals(old.get(entry.key()))) {
                upserts.add(entry);
            }
        }
        List<PerfWorldHotspotIndex.Key> removals = old.keySet().stream()
                .filter(key -> !next.containsKey(key))
                .toList();
        return new PayloadEntries(List.copyOf(upserts), removals);
    }

    private Map<PerfWorldHotspotIndex.Key, PerfWorldHotspotPayload.Entry> wireIndex(
            List<PerfWorldHotspotPayload.Entry> entries) {
        Map<PerfWorldHotspotIndex.Key, PerfWorldHotspotPayload.Entry> result = new LinkedHashMap<>();
        for (PerfWorldHotspotPayload.Entry entry : entries) {
            result.put(entry.key(), entry);
        }
        return result;
    }

    private List<PerfWorldHotspotPayload.Entry> selectWire(
            PerfWorldHotspotIndex index,
            PerfWorldHotspotSelector.View view,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return PerfWorldHotspotSelector.select(index, view, Integer.MAX_VALUE).stream()
                .map(this::toWire)
                .filter(java.util.Objects::nonNull)
                .limit(limit)
                .toList();
    }

    private PerfWorldHotspotPayload.Entry toWire(PerfWorldHotspotIndex.Entry entry) {
        int registryId = registryIds.resolve(entry.detailKind(), entry.detailId());
        return registryId < 0 ? null : new PerfWorldHotspotPayload.Entry(
                entry.key(), entry.detailKind(), registryId,
                entry.measurementStartTick(), entry.samples(), entry.maxSamplesQ8());
    }

    private static int builtInRegistryId(TickDetailKind kind, ResourceLocation id) {
        return switch (kind) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id)
                    .map(BuiltInRegistries.ITEM::getId).orElse(-1);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .map(BuiltInRegistries.ENTITY_TYPE::getId).orElse(-1);
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(id)
                    .map(BuiltInRegistries.BLOCK::getId).orElse(-1);
            case NONE -> -1;
        };
    }

    record Cursor(long streamEpoch,
                  long revision,
                  ResourceLocation dimension,
                  double centerX,
                  double centerZ) {
        PerfWorldHotspotSelector.View view() {
            return new PerfWorldHotspotSelector.View(
                    dimension.toString(), centerX, centerZ);
        }
    }

    record Emission(PerfWorldHotspotPayload payload, Cursor cursor) {
    }

    private record PayloadEntries(List<PerfWorldHotspotPayload.Entry> upserts,
                                  List<PerfWorldHotspotIndex.Key> removals) {
    }

    @FunctionalInterface
    interface RegistryIdResolver {
        int resolve(TickDetailKind kind, ResourceLocation id);
    }
}
