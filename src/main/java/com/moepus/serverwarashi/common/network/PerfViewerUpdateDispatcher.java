package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.performance.report.PerfReportData;
import com.moepus.serverwarashi.common.modules.performance.report.PerfWorldHotspotIndex;

import java.util.UUID;

final class PerfViewerUpdateDispatcher<P> {
    private final PerfViewerSubscriptions subscriptions;
    private final Sender<P> sender;

    PerfViewerUpdateDispatcher(PerfViewerSubscriptions subscriptions, Sender<P> sender) {
        this.subscriptions = subscriptions;
        this.sender = sender;
    }

    void dispatch(UUID viewer, P recipient, Update update) {
        if (subscriptions.isScreenViewer(viewer)) {
            sender.report(recipient, update.report(),
                    subscriptions.nextMode(viewer), update.profiling());
        }
        if (subscriptions.isMapViewer(viewer)) {
            sender.spatial(recipient, update.report(), SpatialTarget.MAP, update.profiling());
        } else if (subscriptions.receivesWorldProjection(viewer)) {
            sender.spatial(recipient, update.report(), SpatialTarget.WORLD, update.profiling());
        }
        if (subscriptions.receivesWorldHotspots(viewer)) {
            sender.hotspots(recipient, update.hotspots());
        }
    }

    record Update(PerfReportData report,
                  PerfWorldHotspotIndex hotspots,
                  boolean profiling) {
    }

    enum SpatialTarget {
        MAP,
        WORLD
    }

    interface Sender<P> {
        void report(P recipient, PerfReportData report,
                    PerfReportPayload.Mode mode, boolean profiling);

        void spatial(P recipient, PerfReportData report,
                     SpatialTarget target, boolean profiling);

        void hotspots(P recipient, PerfWorldHotspotIndex hotspots);
    }
}
