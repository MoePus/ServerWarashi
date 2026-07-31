package com.moepus.serverwarashi.common.network;

import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderFreezeService;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class LoaderFreezeDelivery {
    private LoaderFreezeDelivery() {
    }

    static void handle(ServerPlayer player, LoaderFreezeActionPayload action) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.hasPermissions(2)) {
            sendFailure(player, action, LoaderControlStatePayload.Status.DENIED);
            return;
        }
        BlockLoaderRef ref = ref(action);
        TicketPerfRuntime.LoaderTargetLookup lookup =
                TicketPerfRuntime.findLoaderTarget(action.reportId(), ref);
        if (lookup.status() != TicketPerfRuntime.LoaderLookupStatus.FOUND) {
            sendFailure(player, action, status(lookup.status()));
            return;
        }
        ServerLevel level = server.getLevel(ref.dimension());
        if (level == null) {
            sendFailure(player, action, LoaderControlStatePayload.Status.MISSING);
            return;
        }
        LoaderFreezeService.ApplyRequest request = new LoaderFreezeService.ApplyRequest(
                ref, lookup.targets(), action.frozen());
        LoaderFreezeService.ApplyResult applied = LoaderFreezeService.apply(level, request);
        if (applied.status() == LoaderFreezeService.ApplyStatus.MISSING) {
            sendFailure(player, action, LoaderControlStatePayload.Status.MISSING);
            return;
        }
        if (applied.status() == LoaderFreezeService.ApplyStatus.INVALID) {
            sendFailure(player, action, LoaderControlStatePayload.Status.UNSUPPORTED);
            return;
        }
        LoaderControlStatePayload.Status result = applied.status() == LoaderFreezeService.ApplyStatus.APPLIED
                ? LoaderControlStatePayload.Status.APPLIED
                : LoaderControlStatePayload.Status.UNCHANGED;
        var controlState = new TicketPerfRuntime.LoaderControlState(
                ref, action.frozen(), applied.working());
        var update = TicketPerfRuntime.updateLoaderControl(action.reportId(), controlState);
        if (update.isEmpty()) {
            sendFailure(player, action, LoaderControlStatePayload.Status.STALE);
            return;
        }
        publish(server, state(update.get(), result), player);
    }

    static void publishQueued(MinecraftServer server) {
        for (TicketPerfRuntime.LoaderControlUpdate update
                : TicketPerfRuntime.drainLoaderControlUpdates()) {
            publish(server, state(update, LoaderControlStatePayload.Status.SYNC), null);
        }
    }

    private static BlockLoaderRef ref(LoaderFreezeActionPayload action) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, action.dimension());
        return new BlockLoaderRef(dimension, action.controllerId(), action.ownerPos());
    }

    private static LoaderControlStatePayload state(
            TicketPerfRuntime.LoaderControlUpdate update,
            LoaderControlStatePayload.Status status) {
        BlockLoaderRef ref = update.ref();
        return new LoaderControlStatePayload(update.reportId(), ref.dimension().location(),
                ref.controllerId(), ref.ownerPos(), update.manuallyFrozen(), update.working(), status);
    }

    private static LoaderControlStatePayload.Status status(
            TicketPerfRuntime.LoaderLookupStatus status) {
        return switch (status) {
            case STALE -> LoaderControlStatePayload.Status.STALE;
            case MISSING -> LoaderControlStatePayload.Status.MISSING;
            case UNSUPPORTED -> LoaderControlStatePayload.Status.UNSUPPORTED;
            case FOUND -> throw new IllegalArgumentException("Found lookup is not a failure");
        };
    }

    private static void sendFailure(ServerPlayer player,
                                    LoaderFreezeActionPayload action,
                                    LoaderControlStatePayload.Status status) {
        WarashiNetwork.sendLoaderControlState(player, new LoaderControlStatePayload(
                action.reportId(), action.dimension(), action.controllerId(), action.ownerPos(),
                false, false, status));
    }

    private static void publish(MinecraftServer server,
                                LoaderControlStatePayload state,
                                ServerPlayer requester) {
        Set<UUID> sent = new HashSet<>();
        if (requester != null) {
            WarashiNetwork.sendLoaderControlState(requester, state);
            sent.add(requester.getUUID());
        }
        for (UUID viewerId : PerfReportDelivery.mapViewers()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer != null && viewer.hasPermissions(2) && sent.add(viewerId)
                    && WarashiNetwork.canSendPerfReport(viewer)) {
                WarashiNetwork.sendLoaderControlState(viewer, synchronizedState(state));
            }
        }
    }

    private static LoaderControlStatePayload synchronizedState(LoaderControlStatePayload state) {
        if (state.status() == LoaderControlStatePayload.Status.SYNC) {
            return state;
        }
        return new LoaderControlStatePayload(
                state.reportId(), state.dimension(), state.controllerId(), state.ownerPos(),
                state.manuallyFrozen(), state.working(), LoaderControlStatePayload.Status.SYNC);
    }
}
