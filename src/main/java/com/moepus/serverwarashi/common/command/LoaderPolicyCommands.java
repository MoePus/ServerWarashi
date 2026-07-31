package com.moepus.serverwarashi.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.moepus.serverwarashi.common.core.ticket.TicketScanService;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderRef;
import com.moepus.serverwarashi.common.modules.loaderfreeze.BlockLoaderTicketResolver;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderFreezeService;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderStateData;
import com.moepus.serverwarashi.common.modules.loaderfreeze.LoaderTicketTarget;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LoaderPolicyCommands {
    private static final int MAX_LISTED_POLICIES = 100;
    private static final Comparator<PolicyView> POLICY_ORDER = Comparator
            .comparing(PolicyView::dimension)
            .thenComparing(view -> view.policy().identity().controllerId().toString())
            .thenComparingInt(view -> view.policy().identity().ownerPos().getX())
            .thenComparingInt(view -> view.policy().identity().ownerPos().getY())
            .thenComparingInt(view -> view.policy().identity().ownerPos().getZ());

    private LoaderPolicyCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("loader")
                .then(Commands.literal("frozen")
                        .then(Commands.literal("list")
                                .executes(LoaderPolicyCommands::list))
                        .then(Commands.literal("clear-orphans")
                                .executes(LoaderPolicyCommands::clearOrphans))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("controller", ResourceLocationArgument.id())
                                                .then(Commands.argument("owner", BlockPosArgument.blockPos())
                                                        .executes(LoaderPolicyCommands::clear)))))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<PolicyView> policies = new ArrayList<>();
        for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
            LoaderStateData.get(level).frozenPolicies().forEach(policy -> policies.add(
                    new PolicyView(level.dimension().location().toString(), policy)));
        }
        policies.sort(POLICY_ORDER);
        policies.stream().limit(MAX_LISTED_POLICIES).forEach(policy ->
                context.getSource().sendSuccess(() -> Component.literal(policy.describe()), false));
        int omitted = Math.max(0, policies.size() - MAX_LISTED_POLICIES);
        context.getSource().sendSuccess(() -> Component.literal(
                "Frozen loader policies: " + policies.size()
                        + (omitted == 0 ? "" : " (" + omitted + " more not shown)")), false);
        return policies.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        ResourceLocation controller = ResourceLocationArgument.getId(context, "controller");
        BlockPos owner = BlockPosArgument.getBlockPos(context, "owner");
        BlockLoaderRef ref = BlockLoaderRef.of(level, controller, owner);
        List<LoaderTicketTarget> targets = findTargets(level, ref);
        LoaderFreezeService.ApplyResult result = targets.isEmpty()
                ? clearMissingPolicy(level, ref)
                : LoaderFreezeService.apply(level, new LoaderFreezeService.ApplyRequest(
                ref, targets, false));
        if (result.status() == LoaderFreezeService.ApplyStatus.UNCHANGED) {
            context.getSource().sendFailure(Component.literal("Loader policy was not frozen."));
            return 0;
        }
        syncControl(ref, result.working());
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared loader policy for " + describe(ref)
                        + "; updated " + result.changedTickets() + " ticket(s)."), true);
        return 1;
    }

    private static LoaderFreezeService.ApplyResult clearMissingPolicy(
            ServerLevel level, BlockLoaderRef ref) {
        boolean changed = LoaderStateData.get(level).setManuallyFrozen(
                ref.controllerId(), ref.ownerPos(), false);
        LoaderFreezeService.ApplyStatus status = changed
                ? LoaderFreezeService.ApplyStatus.APPLIED
                : LoaderFreezeService.ApplyStatus.UNCHANGED;
        return new LoaderFreezeService.ApplyResult(status, 0, 0, 0, false);
    }

    private static int clearOrphans(CommandContext<CommandSourceStack> context) {
        int cleared = 0;
        for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
            LoaderStateData data = LoaderStateData.get(level);
            Set<LoaderStateData.LoaderIdentity> live = liveIdentities(level);
            List<LoaderStateData.FrozenPolicy> orphaned = data.frozenPolicies().stream()
                    .filter(policy -> !live.contains(policy.identity()))
                    .toList();
            cleared += data.clearOrphanedPolicies(live);
            orphaned.forEach(policy -> syncControl(new BlockLoaderRef(
                    level.dimension(), policy.identity().controllerId(),
                    policy.identity().ownerPos()), false));
        }
        int result = cleared;
        context.getSource().sendSuccess(() -> Component.literal(
                "Cleared " + result + " orphaned frozen loader policy(s); placer attribution was retained."),
                true);
        return cleared;
    }

    private static List<LoaderTicketTarget> findTargets(ServerLevel level, BlockLoaderRef ref) {
        List<LoaderTicketTarget> result = new ArrayList<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            for (Ticket<?> ticket : tickets) {
                if (BlockLoaderTicketResolver.resolve(level, ticket).filter(ref::equals).isPresent()) {
                    result.add(new LoaderTicketTarget(chunkPos, ticket));
                }
            }
        });
        return List.copyOf(result);
    }

    private static Set<LoaderStateData.LoaderIdentity> liveIdentities(ServerLevel level) {
        Set<LoaderStateData.LoaderIdentity> result = new HashSet<>();
        TicketScanService.visitTicketChunks(level, (chunkPos, tickets) -> {
            for (Ticket<?> ticket : tickets) {
                BlockLoaderTicketResolver.resolve(level, ticket).ifPresent(ref -> result.add(
                        LoaderStateData.LoaderIdentity.of(ref.controllerId(), ref.ownerPos())));
            }
        });
        return Set.copyOf(result);
    }

    private static String describe(BlockLoaderRef ref) {
        BlockPos pos = ref.ownerPos();
        return ref.dimension().location() + " " + ref.controllerId()
                + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void syncControl(BlockLoaderRef ref, boolean working) {
        TicketPerfRuntime.onLoaderControlChanged(
                new TicketPerfRuntime.LoaderControlState(ref, false, working));
    }

    private record PolicyView(String dimension, LoaderStateData.FrozenPolicy policy) {
        String describe() {
            BlockPos pos = policy.identity().ownerPos();
            String placer = policy.placedBy().map(Object::toString).orElse("unknown");
            return dimension + " " + policy.identity().controllerId()
                    + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " placedBy=" + placer;
        }
    }
}
