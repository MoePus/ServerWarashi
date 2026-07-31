package com.moepus.serverwarashi.common.modules.loaderfreeze;

import com.moepus.serverwarashi.common.core.ticket.PauseableTicket;
import com.moepus.serverwarashi.common.core.ticket.TicketPauseBatch;
import com.moepus.serverwarashi.common.core.ticket.TicketUtils;
import com.moepus.serverwarashi.common.mixin.accessor.DistanceManagerAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Applies persistent manual policy to both existing tickets and insertion candidates. */
public final class LoaderFreezeService {
    private LoaderFreezeService() {
    }

    public static Optional<TicketPreparation> prepareInsertion(ServerLevel level, Ticket<?> candidate) {
        return BlockLoaderTicketResolver.resolve(level, candidate).map(ref -> {
            boolean frozen = LoaderStateData.get(level)
                    .isManuallyFrozen(ref.controllerId(), ref.ownerPos());
            TicketPauseBatch.updateTicketPauseReason(
                    candidate, frozen, PauseableTicket.PAUSE_REASON_MANUAL);
            return new TicketPreparation(ref, frozen, candidate);
        });
    }

    public static TicketCompletion completeInsertion(
            ServerLevel level, Ticket<?> stored, TicketPreparation preparation) {
        boolean pauseChanged = TicketPauseBatch.updateTicketPauseReason(
                stored, preparation.frozen(), PauseableTicket.PAUSE_REASON_MANUAL);
        boolean inserted = stored == preparation.candidate();
        if (inserted) {
            LoaderPlacementAttribution.consume(level, preparation.ref());
        }
        return new TicketCompletion(preparation.ref(), inserted, pauseChanged);
    }

    public static ApplyResult apply(ServerLevel level, ApplyRequest request) {
        BlockLoaderRef ref = request.ref();
        if (!level.dimension().equals(ref.dimension())) {
            return ApplyResult.invalid();
        }
        List<LoaderTicketTarget> targets = currentTargets(level, ref, request.capturedTargets());
        if (targets.isEmpty()) {
            return ApplyResult.missing();
        }

        LoaderStateData state = LoaderStateData.get(level);
        boolean policyChanged = state.setManuallyFrozen(
                ref.controllerId(), ref.ownerPos(), request.frozen());
        TicketPauseBatch batch = new TicketPauseBatch(level);
        int changedTickets = 0;
        for (LoaderTicketTarget target : targets) {
            if (batch.apply(target.ticket(), target.centerChunkPos(), request.frozen(),
                    PauseableTicket.PAUSE_REASON_MANUAL)) {
                changedTickets++;
            }
        }
        int updatedChunks = batch.flush();
        boolean working = targets.stream().anyMatch(target -> !TicketUtils.asPauseable(target.ticket()).serverWarashi$isPaused());
        ApplyStatus status = policyChanged || changedTickets > 0 ? ApplyStatus.APPLIED : ApplyStatus.UNCHANGED;
        return new ApplyResult(status, targets.size(), changedTickets, updatedChunks, working);
    }

    public static boolean hasUnpausedTicket(Iterable<Ticket<?>> tickets) {
        for (Ticket<?> ticket : tickets) {
            if (!TicketUtils.asPauseable(ticket).serverWarashi$isPaused()) {
                return true;
            }
        }
        return false;
    }

    private static List<LoaderTicketTarget> currentTargets(
            ServerLevel level, BlockLoaderRef ref, Iterable<LoaderTicketTarget> capturedTargets) {
        DistanceManagerAccessor manager = TicketUtils.getDistanceManager(level);
        IdentityHashMap<Ticket<?>, Boolean> seen = new IdentityHashMap<>();
        List<LoaderTicketTarget> valid = new ArrayList<>();
        for (LoaderTicketTarget target : capturedTargets) {
            Ticket<?> ticket = target.ticket();
            if (seen.containsKey(ticket)
                    || !isStoredByIdentity(manager, target)
                    || !BlockLoaderTicketResolver.resolve(level, ticket).filter(ref::equals).isPresent()) {
                continue;
            }
            seen.put(ticket, Boolean.TRUE);
            valid.add(target);
        }
        return valid;
    }

    private static boolean isStoredByIdentity(DistanceManagerAccessor manager, LoaderTicketTarget target) {
        SortedArraySet<Ticket<?>> tickets = manager.getTickets().get(target.centerChunkPos());
        if (tickets == null) {
            return false;
        }
        for (Ticket<?> ticket : tickets) {
            if (ticket == target.ticket()) {
                return true;
            }
        }
        return false;
    }

    public enum ApplyStatus {
        APPLIED,
        UNCHANGED,
        MISSING,
        INVALID
    }

    public record TicketPreparation(BlockLoaderRef ref, boolean frozen, Ticket<?> candidate) {
        public TicketPreparation {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    public record TicketCompletion(BlockLoaderRef ref, boolean inserted, boolean storedPauseChanged) {
    }

    public record ApplyRequest(BlockLoaderRef ref, List<LoaderTicketTarget> capturedTargets, boolean frozen) {
        public ApplyRequest {
            Objects.requireNonNull(ref, "ref");
            capturedTargets = List.copyOf(capturedTargets);
        }
    }

    public record ApplyResult(ApplyStatus status, int validTickets, int changedTickets,
                              int updatedChunks, boolean working) {
        static ApplyResult missing() {
            return new ApplyResult(ApplyStatus.MISSING, 0, 0, 0, false);
        }

        static ApplyResult invalid() {
            return new ApplyResult(ApplyStatus.INVALID, 0, 0, 0, false);
        }
    }
}
