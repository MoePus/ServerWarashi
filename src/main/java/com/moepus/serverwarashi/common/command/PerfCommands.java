package com.moepus.serverwarashi.common.command;

import com.moepus.serverwarashi.common.network.ReportDelivery;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfSessionController;
import com.moepus.serverwarashi.common.modules.performance.report.TicketPerfOwnerOutput;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.core.owner.OwnerChunks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * ChunkPerf 命令入口。
 * 层级：命令入口层。
 * 上游：WarashiCommands。下游：TicketPerfSessionController / TicketPerfOwnerOutput。
 */
final class PerfCommands {
    private PerfCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("perf")
                .then(Commands.literal("list")
                        .then(listAction("running", ListOwnersOptions.running(OwnerChunks.SortMode.BLOCK_ENTITY))
                                .then(listAction("entity", ListOwnersOptions.running(OwnerChunks.SortMode.ENTITY)))
                                .then(listAction("blockentity", ListOwnersOptions.running(OwnerChunks.SortMode.BLOCK_ENTITY))))
                        .then(listAction("all", ListOwnersOptions.all())))
                .then(Commands.literal("analyze")
                        .then(Commands.literal("start")
                                .then(withOptionalSeconds(Commands.literal("all"), PerfCommands::startAllOwners))
                                .then(withOptionalSeconds(Commands.literal("here"), PerfCommands::startPerfHere))
                                .then(Commands.literal("at")
                                        .then(withOptionalSeconds(
                                                Commands.argument("pos", BlockPosArgument.blockPos()),
                                                PerfCommands::startPerfAt))
                                )
                        )
                        .then(Commands.literal("stop")
                                .executes(context -> {
                                    stopPerfAnalysis(context);
                                    return 1;
                                })
                        )
                )
        );
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withOptionalSeconds(
            T node, Consumer<CommandContext<CommandSourceStack>> action) {
        // 同一动作挂"裸执行 + 带可选 seconds 执行"两条分支，消除重复
        return node
                .executes(context -> {
                    action.accept(context);
                    return 1;
                })
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, TicketPerfConfig.MAX_ANALYZE_SECONDS))
                        .executes(context -> {
                            action.accept(context);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listAction(String name, ListOwnersOptions options) {
        return Commands.literal(name).executes(context -> {
            listOwners(context, options);
            return 1;
        });
    }

    private static void startPerfHere(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        runStartAt(context, pos);
    }

    private static void startPerfAt(CommandContext<CommandSourceStack> context) {
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        runStartAt(context, pos);
    }

    private static void runStartAt(CommandContext<CommandSourceStack> context,
                                   BlockPos pos) {
        int seconds = getOptionalSeconds(context);
        UUID playerId = getPlayerId(context.getSource());
        context.getSource().sendSuccess(() ->
                TicketPerfSessionController.start(
                        context.getSource().getLevel(),
                        pos,
                        seconds,
                        playerId
                ), false);
    }

    private static void stopPerfAnalysis(CommandContext<CommandSourceStack> context) {
        var result = TicketPerfSessionController.stopCurrentAnalysis();
        if (result.report() != null) {
            ReportDelivery.toCommandSource(context.getSource(), result::report);
        } else {
            context.getSource().sendSuccess(result::message, false);
        }
    }

    private static void startAllOwners(CommandContext<CommandSourceStack> context) {
        int seconds = getOptionalSeconds(context);
        UUID playerId = getPlayerId(context.getSource());
        context.getSource().sendSuccess(() ->
                TicketPerfSessionController.startAll(
                        context.getSource().getLevel(),
                        seconds,
                        playerId
                ), false);
    }

    private static void listOwners(CommandContext<CommandSourceStack> context,
                                   ListOwnersOptions options) {
        var source = context.getSource();
        ReportDelivery.toCommandSource(
                source,
                () -> TicketPerfOwnerOutput.ownerReport(
                        source.getLevel(),
                        options.pauseMode(),
                        options.header(),
                        options.sortMode()
                )
        );
    }

    private static int getOptionalSeconds(CommandContext<CommandSourceStack> context) {
        if (context.getNodes().stream().noneMatch(node -> "seconds".equals(node.getNode().getName()))) {
            return TicketPerfConfig.defaultAnalyzeSeconds();
        }
        return IntegerArgumentType.getInteger(context, "seconds");
    }

    private static UUID getPlayerId(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer sp) {
            return sp.getUUID();
        }
        return null;
    }

    private record ListOwnersOptions(OwnerChunks.PauseMode pauseMode,
                                     String header,
                                     OwnerChunks.SortMode sortMode) {
        static ListOwnersOptions running(OwnerChunks.SortMode sortMode) {
            return new ListOwnersOptions(
                    OwnerChunks.PauseMode.ACTIVE_ONLY,
                    "Running ticket owners:",
                    sortMode
            );
        }

        static ListOwnersOptions all() {
            return new ListOwnersOptions(
                    OwnerChunks.PauseMode.ALL,
                    "All ticket owners:",
                    OwnerChunks.SortMode.BLOCK_ENTITY
            );
        }
    }
}
