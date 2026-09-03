package com.moepus.serverwarashi.common.network;

import com.mojang.logging.LogUtils;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackClusterer;
import com.moepus.serverwarashi.common.modules.performance.sampling.UnknownStackSnapshot;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

public final class UnknownStackDetailDelivery {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UnknownStackRequestGate REQUEST_GATE = new UnknownStackRequestGate();
    private static final UnknownStackComputationCache COMPUTATIONS =
            new UnknownStackComputationCache(Util.backgroundExecutor());
    private UnknownStackDetailDelivery() {
    }

    public static void handle(ServerPlayer player, UnknownStackDetailsRequestPayload request) {
        UUID playerId = player.getUUID();
        if (!REQUEST_GATE.tryBegin(playerId, request.requestId(), System.nanoTime())) {
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.BUSY));
            return;
        }
        if (!PerfSubscriptionAccess.isPermitted(player)) {
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.DENIED));
            return;
        }
        if (!TicketPerfRuntime.hasUnknownStackReport(request.reportId())) {
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.STALE));
            return;
        }
        UnknownStackComputationCache.Computation current =
                COMPUTATIONS.current(request.reportId(),
                        TicketPerfRuntime.isUnknownStackReportFinalized(request.reportId()));
        if (current != null) {
            attach(player, request, current);
            return;
        }
        UnknownStackSnapshot snapshot = TicketPerfRuntime
                .unknownStackSnapshot(request.reportId()).orElse(null);
        if (snapshot == null) {
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.STALE));
            return;
        }
        if (snapshot.retainedCount() == 0) {
            sendAndFinish(player, request, empty(request, snapshot));
            return;
        }
        startOrJoin(player, request, snapshot);
    }

    public static void onLogout(ServerPlayer player) {
        REQUEST_GATE.remove(player.getUUID());
    }

    public static void clearRuntimeState() {
        REQUEST_GATE.clear();
        COMPUTATIONS.clear();
    }

    private static void startOrJoin(ServerPlayer player,
                                    UnknownStackDetailsRequestPayload request,
                                    UnknownStackSnapshot snapshot) {
        UnknownStackComputationCache.Computation computation;
        try {
            computation = COMPUTATIONS.acquire(snapshot);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not schedule Unknown stack clustering", exception);
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.ERROR));
            return;
        }
        if (computation == null) {
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.BUSY));
            return;
        }
        attach(player, request, computation);
    }

    private static void attach(ServerPlayer player,
                               UnknownStackDetailsRequestPayload request,
                               UnknownStackComputationCache.Computation computation) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            sendAndFinish(player, request, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.ERROR));
            return;
        }
        var context = new CompletionContext(server, player.getUUID(), request, computation);
        computation.future().whenComplete((details, failure) ->
                server.execute(() -> complete(context, details, failure)));
    }

    private static void complete(CompletionContext context,
                                 UnknownStackPreparedDetails details,
                                 Throwable completionFailure) {
        UnknownStackDetailsRequestPayload request = context.request;
        COMPUTATIONS.release(context.computation);
        finishWithoutPlayer(context.playerId, request.requestId());
        ServerPlayer player = context.server.getPlayerList().getPlayer(context.playerId);
        if (player == null) {
            return;
        }
        if (!TicketPerfRuntime.hasUnknownStackReport(request.reportId())) {
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.STALE));
            return;
        }
        if (TicketPerfRuntime.isUnknownStackReportFinalized(request.reportId())
                != context.computation.key().finalized()) {
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.STALE));
            return;
        }
        if (!PerfSubscriptionAccess.isPermitted(player)) {
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.DENIED));
            return;
        }
        if (completionFailure != null || details == null) {
            LOGGER.error("Could not prepare Unknown stack details", completionFailure);
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.ERROR));
            return;
        }
        try {
            send(player, details.payload(request.requestId()));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not create Unknown stack details payload", exception);
            send(player, UnknownStackDetailsPayload.failed(
                    request.requestId(), request.reportId(),
                    UnknownStackDetailsPayload.Status.ERROR));
        }
    }

    private static void finishWithoutPlayer(UUID playerId, long requestId) {
        REQUEST_GATE.finish(playerId, requestId);
    }

    private static UnknownStackDetailsPayload empty(
            UnknownStackDetailsRequestPayload request, UnknownStackSnapshot snapshot) {
        return new UnknownStackDetailsPayload(request.requestId(), request.reportId(),
                snapshot.revision(), UnknownStackDetailsPayload.Status.EMPTY,
                snapshot.totalObserved(), snapshot.retainedCount(), List.of());
    }

    static UnknownStackDetailsPayload response(
            UnknownStackDetailsRequestPayload request,
            UnknownStackClusterer.Result result) {
        return UnknownStackPreparedDetails.from(result).payload(request.requestId());
    }

    private static void send(ServerPlayer player, UnknownStackDetailsPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void sendAndFinish(ServerPlayer player,
                                      UnknownStackDetailsRequestPayload request,
                                      UnknownStackDetailsPayload payload) {
        REQUEST_GATE.finish(player.getUUID(), request.requestId());
        send(player, payload);
    }

    private record CompletionContext(MinecraftServer server,
                                     UUID playerId,
                                     UnknownStackDetailsRequestPayload request,
                                     UnknownStackComputationCache.Computation computation) {
    }
}
