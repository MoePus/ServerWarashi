package com.moepus.serverwarashi.common.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class ClientPayloadHandlers {
    private static Consumer<PerfReportPayload> perfReportHandler = ignored -> {
    };

    private ClientPayloadHandlers() {
    }

    public static void setPerfReportHandler(Consumer<PerfReportPayload> handler) {
        perfReportHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handlePerfReport(PerfReportPayload payload) {
        perfReportHandler.accept(payload);
    }

}
