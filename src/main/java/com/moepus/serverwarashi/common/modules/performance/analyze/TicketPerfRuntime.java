package com.moepus.serverwarashi.common.modules.performance.analyze;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;
import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackSnapshot;
import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.PerfAttributionSnapshot;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TicketPerfRuntime {
    private static final PerfSessionController CONTROLLER = new PerfSessionController();
    private static final LoaderControlUpdateQueue LOADER_UPDATES = new LoaderControlUpdateQueue();
    private static PerfReportData materializedBase;
    private static PerfReportData materializedReport;
    private static PerfAttributionSnapshot materializedAttribution;
    private static long materializedControlRevision = Long.MIN_VALUE;

    private TicketPerfRuntime() {
    }

    public static StartResult start(MinecraftServer server, int durationSeconds, UUID playerId) {
        if (!CONTROLLER.start(server, durationSeconds, playerId)) {
            return new StartResult(Component.literal("Another perf analysis is already running."), false);
        }
        return new StartResult(Component.literal("Performance analysis started for " + durationSeconds
                + "s; tick sampling begins on the next server tick."), true);
    }

    public static void onServerTickPre(MinecraftServer server) {
        CONTROLLER.onServerTickPre(server);
    }

    public static AutoReport onServerPaused() {
        PerfSessionController.Completion completion = CONTROLLER.onServerPaused();
        if (completion == null) {
            return null;
        }
        return new AutoReport(completion.playerId(), materializeLoaderControls(
                completion.data(), CONTROLLER.currentAttribution()));
    }

    public static AutoReport onServerTickPost(MinecraftServer server) {
        PerfSessionController.Completion completion = CONTROLLER.onServerTickPost(server);
        if (completion == null) {
            return null;
        }
        return new AutoReport(completion.playerId(), materializeLoaderControls(
                completion.data(), CONTROLLER.currentAttribution()));
    }

    public static StopResult stopCurrentAnalysis(MinecraftServer server) {
        return stopCurrentAnalysis(server != null && server.isPaused());
    }

    private static StopResult stopCurrentAnalysis(boolean retainInitialLayout) {
        PerfReportData data = CONTROLLER.stop(retainInitialLayout);
        if (data != null) {
            return new StopResult(null, materializeLoaderControls(data, CONTROLLER.currentAttribution()));
        }
        return new StopResult(Component.literal("No active perf analysis."), null);
    }

    public static boolean isProfiling() {
        return CONTROLLER.hasActiveSession();
    }

    public static PerfReportData currentReport() {
        PerfReportData report = currentReportOrNull();
        return report == null ? PerfReportData.empty(TicketPerfConfig.sampleIntervalMicros()) : report;
    }

    public static PerfReportData currentReportOrNull() {
        return materializeLoaderControls(currentPublishedReport(), CONTROLLER.currentAttribution());
    }

    public static DeliverySnapshot currentDeliverySnapshot() {
        var published = CONTROLLER.currentPublishedSnapshot();
        PerfReportData report = materializeLoaderControls(
                published.report(), CONTROLLER.currentAttribution());
        if (report == null) {
            report = PerfReportData.empty(TicketPerfConfig.sampleIntervalMicros());
        }
        PerfWorldHotspotIndex hotspots = published.hotspots() == null
                ? PerfWorldHotspotIndex.empty(0L, 0L, report.tickCount())
                : published.hotspots();
        return new DeliverySnapshot(report, hotspots);
    }

    public static boolean clearLastReport() {
        boolean cleared = CONTROLLER.clearLastReport();
        if (cleared) {
            LOADER_UPDATES.clear();
            clearMaterializedReport();
        }
        return cleared;
    }

    public static Optional<UnknownStackSnapshot> unknownStackSnapshot(long reportId) {
        return Optional.ofNullable(CONTROLLER.unknownStackSnapshot(reportId));
    }

    public static boolean hasUnknownStackReport(long reportId) {
        return CONTROLLER.hasUnknownStackReport(reportId);
    }

    public static boolean isUnknownStackReportFinalized(long reportId) {
        return CONTROLLER.isUnknownStackReportFinalized(reportId);
    }

    public static void clearRuntimeState() {
        CONTROLLER.clear();
        LOADER_UPDATES.clear();
        clearMaterializedReport();
    }

    public static LoaderTargetLookup findLoaderTarget(long reportId, BlockLoaderRef ref) {
        PerfReportData report = currentPublishedReport();
        PerfAttributionSnapshot attribution = CONTROLLER.currentAttribution();
        if (report == null || attribution == null || reportId <= 0L || report.reportId() != reportId) {
            return LoaderTargetLookup.failed(LoaderLookupStatus.STALE);
        }
        List<LoaderTicketTarget> targets = attribution.loaderTargets(ref);
        if (!hasReportedControl(report, ref)) {
            return LoaderTargetLookup.failed(targets.isEmpty()
                    ? LoaderLookupStatus.MISSING : LoaderLookupStatus.UNSUPPORTED);
        }
        Optional<PerfAttributionSnapshot.LoaderControl> control = attribution.loaderControl(ref);
        return targets.isEmpty() || control.isEmpty()
                ? LoaderTargetLookup.failed(LoaderLookupStatus.MISSING)
                : new LoaderTargetLookup(LoaderLookupStatus.FOUND, targets, control.get());
    }

    public static Optional<LoaderControlUpdate> updateLoaderControl(
            long reportId, LoaderControlState state) {
        PerfReportData report = currentPublishedReport();
        PerfAttributionSnapshot attribution = CONTROLLER.currentAttribution();
        if (report == null || attribution == null || report.reportId() != reportId
                || !hasReportedControl(report, state.ref())) {
            return Optional.empty();
        }
        attribution.updateLoaderControl(
                state.ref(), state.manuallyFrozen(), state.working());
        return attribution.loaderControl(state.ref())
                .map(control -> LOADER_UPDATES.acceptAuthoritative(update(reportId, control)));
    }

    public static void onLoaderControlChanged(LoaderControlState state) {
        PerfReportData report = currentPublishedReport();
        PerfAttributionSnapshot attribution = CONTROLLER.currentAttribution();
        if (report == null || attribution == null || !hasReportedControl(report, state.ref())) {
            return;
        }
        attribution.updateLoaderControl(
                state.ref(), state.manuallyFrozen(), state.working());
        attribution.loaderControl(state.ref())
                .map(control -> update(report.reportId(), control))
                .ifPresent(LOADER_UPDATES::put);
    }

    public static void onLoaderTicketInserted(
            ServerLevel level, BlockLoaderRef ref, LoaderTicketTarget target) {
        if (!level.dimension().equals(ref.dimension())) {
            return;
        }
        PerfReportData report = currentPublishedReport();
        PerfAttributionSnapshot attribution = CONTROLLER.currentAttribution();
        if (report == null || attribution == null || !hasReportedControl(report, ref)
                || !attribution.appendLoaderTarget(ref, target)) {
            return;
        }
        attribution.loaderControl(ref)
                .map(control -> update(report.reportId(), control))
                .ifPresent(LOADER_UPDATES::put);
    }

    public static void onLoaderTicketRemoved(Ticket<?> stored) {
        PerfAttributionSnapshot attribution = CONTROLLER.currentAttribution();
        if (attribution != null) {
            attribution.removeLoaderTarget(stored);
        }
    }

    public static List<LoaderControlUpdate> drainLoaderControlUpdates() {
        return LOADER_UPDATES.drain();
    }

    private static PerfReportData currentPublishedReport() {
        return CONTROLLER.currentPublishedSnapshot().report();
    }

    private static boolean hasReportedControl(PerfReportData report, BlockLoaderRef ref) {
        String dimension = ref.dimension().location().toString();
        return report.loaderSources().stream().anyMatch(source -> {
            PerfReportData.LoaderControl control = source.control();
            return dimension.equals(source.dimension()) && control != null
                    && ref.controllerId().equals(control.controllerId())
                    && ref.ownerPos().equals(control.ownerPos());
        });
    }

    private static LoaderControlUpdate update(
            long reportId, PerfAttributionSnapshot.LoaderControl control) {
        return new LoaderControlUpdate(reportId, control.ref(),
                control.manuallyFrozen(), control.working());
    }

    static PerfReportData withLiveLoaderControls(
            PerfReportData report, PerfAttributionSnapshot attribution) {
        if (report == null || attribution == null || report.loaderSources().isEmpty()) {
            return report;
        }
        List<PerfReportData.LoaderSourceEntry> sources = report.loaderSources().stream()
                .map(source -> withLiveLoaderControl(source, attribution))
                .toList();
        return report.withLoaderSources(sources);
    }

    private static PerfReportData materializeLoaderControls(
            PerfReportData report, PerfAttributionSnapshot attribution) {
        long revision = attribution == null ? Long.MIN_VALUE : attribution.loaderControlRevision();
        if (report == materializedBase && attribution == materializedAttribution
                && revision == materializedControlRevision) {
            return materializedReport;
        }
        materializedBase = report;
        materializedAttribution = attribution;
        materializedControlRevision = revision;
        materializedReport = withLiveLoaderControls(report, attribution);
        return materializedReport;
    }

    private static void clearMaterializedReport() {
        materializedBase = null;
        materializedReport = null;
        materializedAttribution = null;
        materializedControlRevision = Long.MIN_VALUE;
    }

    private static PerfReportData.LoaderSourceEntry withLiveLoaderControl(
            PerfReportData.LoaderSourceEntry source, PerfAttributionSnapshot attribution) {
        PerfReportData.LoaderControl reported = source.control();
        ResourceLocation dimensionId = ResourceLocation.tryParse(source.dimension());
        if (reported == null || dimensionId == null) {
            return source;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockLoaderRef ref = new BlockLoaderRef(
                dimension, reported.controllerId(), reported.ownerPos());
        PerfReportData.LoaderControl current = attribution.loaderControl(ref)
                .map(control -> new PerfReportData.LoaderControl(control.ref().controllerId(),
                        control.ref().ownerPos(), control.manuallyFrozen(), control.working()))
                .orElse(null);
        return source.withControl(current);
    }

    public record AutoReport(UUID playerId, PerfReportData data) {
    }

    public record DeliverySnapshot(PerfReportData report, PerfWorldHotspotIndex hotspots) {
    }

    public record StartResult(Component message, boolean started) {
    }

    public record StopResult(Component message, PerfReportData data) {
    }

    public enum LoaderLookupStatus {
        FOUND,
        STALE,
        MISSING,
        UNSUPPORTED
    }

    public record LoaderTargetLookup(LoaderLookupStatus status,
                                     List<LoaderTicketTarget> targets,
                                     PerfAttributionSnapshot.LoaderControl control) {
        public LoaderTargetLookup {
            targets = List.copyOf(targets);
        }

        static LoaderTargetLookup failed(LoaderLookupStatus status) {
            return new LoaderTargetLookup(status, List.of(), null);
        }
    }

    public record LoaderControlUpdate(long reportId,
                                      BlockLoaderRef ref,
                                      boolean manuallyFrozen,
                                      boolean working) {
    }

    public record LoaderControlState(BlockLoaderRef ref,
                                     boolean manuallyFrozen,
                                     boolean working) {
        public LoaderControlState {
            Objects.requireNonNull(ref, "ref");
        }
    }

    static final class LoaderControlUpdateQueue {
        private final Map<BlockLoaderRef, LoaderControlUpdate> pending = new LinkedHashMap<>();

        void put(LoaderControlUpdate update) {
            pending.put(update.ref(), update);
        }

        LoaderControlUpdate acceptAuthoritative(LoaderControlUpdate update) {
            pending.remove(update.ref());
            return update;
        }

        List<LoaderControlUpdate> drain() {
            List<LoaderControlUpdate> updates = List.copyOf(pending.values());
            pending.clear();
            return updates;
        }

        void clear() {
            pending.clear();
        }
    }
}
