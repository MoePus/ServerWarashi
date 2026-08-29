package com.moepus.serverwarashi.client.state;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickDetailKind;
import com.moepus.serverwarashi.common.network.PerfWorldHotspotPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientPerfWorldState {
    private static final Map<PerfWorldHotspotIndex.Key, PerfWorldHotspotPayload.Entry> ENTRIES =
            new LinkedHashMap<>();
    private static Snapshot state = Snapshot.empty(0L);

    private ClientPerfWorldState() {
    }

    static boolean apply(PerfWorldHotspotPayload payload) {
        if (payload.full() ? isOlderFull(payload) : !canApplyDelta(payload)) {
            return false;
        }
        if (payload.full()) {
            ENTRIES.clear();
        }
        payload.removals().forEach(ENTRIES::remove);
        payload.upserts().forEach(entry -> ENTRIES.put(entry.key(), entry));
        long clientRevision = state.clientRevision() + 1L;
        List<Hotspot> hotspots = ENTRIES.values().stream()
                .map(entry -> toHotspot(entry, payload.tickCount(), payload.nanosPerSample()))
                .toList();
        state = new Snapshot(payload.streamEpoch(), payload.revision(), payload.tickCount(),
                payload.nanosPerSample(), payload.dimension().toString(),
                clientRevision, hotspots);
        return true;
    }

    private static boolean canApplyDelta(PerfWorldHotspotPayload payload) {
        return state.streamEpoch() == payload.streamEpoch()
                && state.protocolRevision() == payload.baseRevision()
                && state.dimension().equals(payload.dimension().toString());
    }

    private static boolean isOlderFull(PerfWorldHotspotPayload payload) {
        return state.streamEpoch() == payload.streamEpoch()
                && payload.revision() < state.protocolRevision();
    }

    private static Hotspot toHotspot(PerfWorldHotspotPayload.Entry entry,
                                     long tickCount,
                                     long nanosPerSample) {
        var cost = new PerfWorldHotspotIndex.Cost(entry.samples(), entry.maxSamplesQ8());
        long measurementTicks = measurementTicks(tickCount, entry.measurementStartTick());
        PerfReportData.Metric metric = cost.metric(measurementTicks, nanosPerSample);
        return new Hotspot(entry.key(), entry.detailKind(), entry.registryId(),
                measurementTicks, metric);
    }

    static long measurementTicks(long tickCount, long measurementStartTick) {
        return tickCount <= 0L ? 0L
                : Math.max(1L, tickCount - measurementStartTick + 1L);
    }

    public static Snapshot snapshot() {
        return state;
    }

    static void clear() {
        ENTRIES.clear();
        state = Snapshot.empty(state.clientRevision() + 1L);
    }

    public record Hotspot(PerfWorldHotspotIndex.Key key,
                          TickDetailKind detailKind,
                          int registryId,
                          long measurementTicks,
                          PerfReportData.Metric metric) {
    }

    public record Snapshot(long streamEpoch,
                           long protocolRevision,
                           long tickCount,
                           long nanosPerSample,
                           String dimension,
                           long clientRevision,
                           List<Hotspot> hotspots) {
        public Snapshot {
            hotspots = List.copyOf(hotspots);
        }

        private static Snapshot empty(long clientRevision) {
            return new Snapshot(0L, 0L, 0L, 0L, "", clientRevision, List.of());
        }
    }
}
