package com.moepus.serverwarashi.client.map;

import com.moepus.serverwarashi.api.client.map.MapMenuItem;
import com.moepus.serverwarashi.api.client.map.MapTarget;
import com.moepus.serverwarashi.client.network.LoaderFreezeClient;
import com.moepus.serverwarashi.client.network.WeakChunkClearClient;
import com.moepus.serverwarashi.client.screen.PerfDisplayText;
import com.moepus.serverwarashi.client.state.ClientPerfReportState;
import com.moepus.serverwarashi.client.state.ClientWeakChunkState;
import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.weakload.WeakChunkSnapshot;
import com.moepus.serverwarashi.common.modules.weakload.WeakEntityType;
import com.moepus.serverwarashi.common.network.LoaderFreezeActionPayload;
import com.moepus.serverwarashi.common.network.WeakChunkClearPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds context menu commands shared by map integrations.
 */
public final class MapContextMenuService {
    private static final int MAX_OPTIONS = 12;
    private static final Runnable NO_ACTION = () -> {
    };
    private static final Comparator<LoaderTarget> TARGET_ORDER = Comparator
            .comparing((LoaderTarget target) -> target.controllerId().toString())
            .thenComparingInt(target -> target.ownerPos().getX())
            .thenComparingInt(target -> target.ownerPos().getY())
            .thenComparingInt(target -> target.ownerPos().getZ());
    private static final ClientActions LIVE_CLIENTS = new LiveClientActions();
    private static PendingClear pendingClear;

    private MapContextMenuService() {
    }

    public static List<MapMenuItem> items(MapTarget target) {
        Objects.requireNonNull(target, "target");
        PerfReportData report = ClientPerfReportState.snapshot().report();
        WeakChunkSnapshot.ChunkEntry weakChunk = ClientWeakChunkState.chunk(
                target.dimension().toString(), target.chunkX(), target.chunkZ());
        long reportId = report == null ? 0L : report.reportId();
        return items(target, new MenuState(weakChunk, report, reportId), LIVE_CLIENTS);
    }

    public static void clear() {
        pendingClear = null;
    }

    static List<MapMenuItem> items(MapTarget target, MenuState state, ClientActions clients) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(clients, "clients");
        List<MapMenuItem> items = new ArrayList<>();
        MenuContext context = new MenuContext(target, state, clients);
        addWeakClearItem(items, context);
        addLoaderItems(items, context);
        return List.copyOf(items);
    }

    private static void addWeakClearItem(List<MapMenuItem> items,
                                         MenuContext context) {
        WeakChunkSnapshot.ChunkEntry chunk = context.state().weakChunk();
        ClientActions clients = context.clients();
        if (!clients.canClearWeakChunk() || chunk == null
                || chunk.entityCount() < WeakChunkSnapshot.ENTITY_RED_THRESHOLD) {
            return;
        }
        WeakEntityType type = chunk.topTypes(1).getFirst().getKey();
        PendingClear selection = new PendingClear(context.target(), type);
        boolean confirming = selection.equals(pendingClear);
        String key = confirming
                ? "gui.serverwarashi.weak_clear_confirm"
                : "gui.serverwarashi.weak_clear";
        Component label = Component.translatable(key, PerfDisplayText.sampledEntityName(type));
        items.add(new MapMenuItem(label, true,
                () -> selectWeakClear(selection, confirming, clients)));
    }

    private static void selectWeakClear(PendingClear selection,
                                        boolean confirming,
                                        ClientActions clients) {
        if (!confirming) {
            pendingClear = selection;
            return;
        }
        MapTarget target = selection.target();
        clients.clearWeakChunk(new WeakChunkClearPayload(
                target.dimension().toString(), target.chunkX(), target.chunkZ(), selection.type()));
        pendingClear = null;
    }

    private static void addLoaderItems(List<MapMenuItem> items,
                                       MenuContext context) {
        ClientActions clients = context.clients();
        if (!clients.canControlLoader()) {
            return;
        }
        MenuState state = context.state();
        List<LoaderTarget> targets = loaderTargets(
                state.report(), state.reportId(), context.target());
        int visible = Math.min(targets.size(), MAX_OPTIONS);
        for (int index = 0; index < visible; index++) {
            items.add(loaderItem(targets.get(index), clients));
        }
        if (targets.size() > visible) {
            Component label = Component.translatable(
                    "gui.serverwarashi.loader_control_more", targets.size() - visible);
            items.add(new MapMenuItem(label, false, NO_ACTION));
        }
    }

    private static MapMenuItem loaderItem(LoaderTarget target, ClientActions clients) {
        String key = target.manuallyFrozen()
                ? "gui.serverwarashi.loader_unfreeze"
                : "gui.serverwarashi.loader_freeze";
        Component label = Component.translatable(key, PerfDisplayText.resource(target.loaderName()));
        Runnable action = () -> clients.controlLoader(new LoaderFreezeActionPayload(
                target.reportId(), target.dimension(), target.controllerId(),
                target.ownerPos(), target.requestedFrozen()));
        return new MapMenuItem(label, true, action);
    }

    static List<LoaderTarget> loaderTargets(PerfReportData report,
                                            long reportId,
                                            MapTarget target) {
        if (report == null || report.reportId() <= 0L || report.reportId() != reportId) {
            return List.of();
        }
        Map<LoaderIdentity, LoaderTarget> unique = new LinkedHashMap<>();
        LoaderQuery query = new LoaderQuery(reportId, target);
        for (PerfReportData.LoaderSourceEntry source : report.loaderSources()) {
            addLoaderTarget(unique, source, query);
        }
        return unique.values().stream().sorted(TARGET_ORDER).toList();
    }

    private static void addLoaderTarget(Map<LoaderIdentity, LoaderTarget> targets,
                                        PerfReportData.LoaderSourceEntry source,
                                        LoaderQuery query) {
        PerfReportData.LoaderControl control = source.control();
        if (control == null || source.omittedRegions() > 0
                || !source.dimension().equals(query.target().dimension().toString())
                || !isOwnerChunk(control.ownerPos(), query.target())) {
            return;
        }
        LoaderIdentity identity = new LoaderIdentity(control.controllerId(), control.ownerPos());
        targets.putIfAbsent(identity, new LoaderTarget(
                query.reportId(), query.target().dimension(), new LoaderSource(
                control.controllerId(), control.ownerPos(),
                new LoaderPresentation(source.loaderName(), control.manuallyFrozen()))));
    }

    private static boolean isOwnerChunk(BlockPos ownerPos, MapTarget target) {
        return (ownerPos.getX() >> 4) == target.chunkX()
                && (ownerPos.getZ() >> 4) == target.chunkZ();
    }

    record MenuState(WeakChunkSnapshot.ChunkEntry weakChunk,
                     PerfReportData report,
                     long reportId) {
    }

    private record MenuContext(MapTarget target, MenuState state, ClientActions clients) {
    }

    private record LoaderQuery(long reportId, MapTarget target) {
    }

    interface ClientActions {
        boolean canClearWeakChunk();

        void clearWeakChunk(WeakChunkClearPayload payload);

        boolean canControlLoader();

        void controlLoader(LoaderFreezeActionPayload payload);
    }

    record LoaderTarget(long reportId, ResourceLocation dimension, LoaderSource source) {
        LoaderTarget {
            dimension = Objects.requireNonNull(dimension, "dimension");
            source = Objects.requireNonNull(source, "source");
        }

        ResourceLocation controllerId() {
            return source.controllerId();
        }

        BlockPos ownerPos() {
            return source.ownerPos();
        }

        String loaderName() {
            return source.presentation().loaderName();
        }

        boolean manuallyFrozen() {
            return source.presentation().manuallyFrozen();
        }

        boolean requestedFrozen() {
            return !manuallyFrozen();
        }
    }

    private record LoaderSource(ResourceLocation controllerId,
                                BlockPos ownerPos,
                                LoaderPresentation presentation) {
        private LoaderSource {
            controllerId = Objects.requireNonNull(controllerId, "controllerId");
            ownerPos = Objects.requireNonNull(ownerPos, "ownerPos").immutable();
            presentation = Objects.requireNonNull(presentation, "presentation");
        }
    }

    private record LoaderPresentation(String loaderName, boolean manuallyFrozen) {
        private LoaderPresentation {
            loaderName = Objects.requireNonNull(loaderName, "loaderName");
        }
    }

    private record PendingClear(MapTarget target, WeakEntityType type) {
    }

    private record LoaderIdentity(ResourceLocation controllerId, BlockPos ownerPos) {
    }

    private static final class LiveClientActions implements ClientActions {
        @Override
        public boolean canClearWeakChunk() {
            return WeakChunkClearClient.canSend();
        }

        @Override
        public void clearWeakChunk(WeakChunkClearPayload payload) {
            WeakChunkClearClient.send(payload);
        }

        @Override
        public boolean canControlLoader() {
            return LoaderFreezeClient.canSend();
        }

        @Override
        public void controlLoader(LoaderFreezeActionPayload payload) {
            LoaderFreezeClient.send(payload);
        }
    }
}
