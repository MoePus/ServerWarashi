package com.moepus.serverwarashi.common.command;

import com.moepus.serverwarashi.common.config.TicketPerfConfig;
import com.moepus.serverwarashi.common.network.PerfReportDelivery;
import com.moepus.serverwarashi.common.modules.performance.analyze.TicketPerfRuntime;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

final class TicketPerfCommands {
    private TicketPerfCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("perf")
                .then(Commands.literal("open")
                        .executes(TicketPerfCommands::open))
                .then(Commands.literal("analyze")
                        .then(Commands.literal("start")
                                .executes(TicketPerfCommands::start)
                                .then(Commands.argument("open", BoolArgumentType.bool())
                                        .executes(TicketPerfCommands::start))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(
                                                1, TicketPerfConfig.MAX_ANALYZE_SECONDS))
                                        .executes(TicketPerfCommands::start)
                                        .then(Commands.argument("open", BoolArgumentType.bool())
                                                .executes(TicketPerfCommands::start))))
                        .then(Commands.literal("stop")
                                .executes(TicketPerfCommands::stop))));
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        int seconds = optionalSeconds(context);
        boolean open = optionalOpen(context);
        var source = context.getSource();
        var result = TicketPerfRuntime.start(source.getServer(), seconds, playerId(source));
        if (result.started() && open && source.getEntity() instanceof ServerPlayer player) {
            PerfReportDelivery.open(player, TicketPerfRuntime.currentReport(), true);
        }
        source.sendSuccess(result::message, false);
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!PerfReportDelivery.open(player, TicketPerfRuntime.currentReport(), TicketPerfRuntime.hasSession())) {
            context.getSource().sendFailure(Component.literal(
                    "Client does not support the performance profiler window."));
            return 0;
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        var result = TicketPerfRuntime.stopCurrentAnalysis();
        if (result.data() != null) {
            PerfReportDelivery.toCommandSource(context.getSource(), result.data());
            PerfReportDelivery.tick(context.getSource().getServer(), result.data(), false, true);
        } else {
            context.getSource().sendSuccess(result::message, false);
        }
        return 1;
    }

    private static int optionalSeconds(CommandContext<CommandSourceStack> context) {
        boolean supplied = context.getNodes().stream()
                .anyMatch(node -> "seconds".equals(node.getNode().getName()));
        return supplied
                ? IntegerArgumentType.getInteger(context, "seconds")
                : TicketPerfConfig.autoStopSeconds();
    }

    private static boolean optionalOpen(CommandContext<CommandSourceStack> context) {
        boolean supplied = context.getNodes().stream()
                .anyMatch(node -> "open".equals(node.getNode().getName()));
        return supplied ? BoolArgumentType.getBool(context, "open") : true;
    }

    private static UUID playerId(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }
}
