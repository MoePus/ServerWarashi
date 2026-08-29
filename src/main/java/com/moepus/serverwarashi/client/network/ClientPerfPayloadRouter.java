package com.moepus.serverwarashi.client.network;

import com.moepus.serverwarashi.client.state.ClientPerfStateCoordinator;
import com.moepus.serverwarashi.common.network.PerfSpatialSnapshotPayload;

import java.util.function.Consumer;

public final class ClientPerfPayloadRouter {
    private ClientPerfPayloadRouter() {
    }

    public static void applySpatialSnapshot(PerfSpatialSnapshotPayload payload) {
        applySpatialSnapshot(payload, PerfMapSubscriptionClient::setAnalysisLive);
    }

    static void applySpatialSnapshot(
            PerfSpatialSnapshotPayload payload, Consumer<Boolean> analysisLive) {
        ClientPerfStateCoordinator.applySpatialSnapshot(
                payload.report(), payload.profiling());
        if (!payload.profiling()) {
            analysisLive.accept(false);
        }
    }
}
