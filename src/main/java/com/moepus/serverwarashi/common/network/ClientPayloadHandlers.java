package com.moepus.serverwarashi.common.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class ClientPayloadHandlers {
    private static Consumer<PerfReportPayload> perfReportHandler = ignored -> {
    };
    private static Consumer<WeakChunkPayload> weakChunkHandler = ignored -> {
    };
    private static Consumer<LoaderControlStatePayload> loaderControlHandler = ignored -> {
    };

    private ClientPayloadHandlers() {
    }

    public static void setPerfReportHandler(Consumer<PerfReportPayload> handler) {
        perfReportHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handlePerfReport(PerfReportPayload payload) {
        perfReportHandler.accept(payload);
    }

    public static void setWeakChunkHandler(Consumer<WeakChunkPayload> handler) {
        weakChunkHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handleWeakChunks(WeakChunkPayload payload) {
        weakChunkHandler.accept(payload);
    }

    public static void setLoaderControlHandler(Consumer<LoaderControlStatePayload> handler) {
        loaderControlHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handleLoaderControl(LoaderControlStatePayload payload) {
        loaderControlHandler.accept(payload);
    }

}
