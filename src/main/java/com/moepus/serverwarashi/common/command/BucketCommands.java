package com.moepus.serverwarashi.common.command;

import com.moepus.serverwarashi.common.core.ticket.TicketPauseService;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.moepus.serverwarashi.common.config.TicketBucketConfig;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * 自动分桶模块命令入口。
 * 层级：命令入口层。
 * 上游：WarashiCommands。下游：TicketBucketConfig / TicketPauseService。
 */
final class BucketCommands {
    private BucketCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(net.minecraft.commands.Commands.literal("bucket")
                .then(boolSetting(new BoolSetting(
                        "enabled",
                        "bucket",
                        TicketBucketConfig::setEnabled,
                        TicketBucketConfig::enabled,
                        context -> {
                            // 关闭分桶后立即清除所有维度的 AUTO 暂停，避免残留
                            if (!TicketBucketConfig.enabled()) {
                                context.getSource().getServer().getAllLevels()
                                        .forEach(TicketPauseService::clearAutoPause);
                            }
                        }
                )))
                .then(net.minecraft.commands.Commands.literal("ticket")
                        .then(boolSetting(new BoolSetting(
                                "pauseAll",
                                "pauseAll",
                                TicketBucketConfig::setPauseAll,
                                TicketBucketConfig::pauseAll,
                                context -> {
                                }
                        )))
                        .then(intSetting(new IntSetting(
                                "runEvery",
                                1,
                                1200,
                                TicketBucketConfig::setRunEvery,
                                TicketBucketConfig::runEvery
                        )))
                        .then(intSetting(new IntSetting(
                                "phasePeriod",
                                1,
                                1024,
                                TicketBucketConfig::setPhasePeriod,
                                TicketBucketConfig::phasePeriod
                        )))
                        .then(intSetting(new IntSetting(
                                "minHoldTicks",
                                0,
                                12000,
                                TicketBucketConfig::setMinHoldTicks,
                                TicketBucketConfig::minHoldTicks
                        )))
                        .then(intSetting(new IntSetting(
                                "pauseDelayRuns",
                                0,
                                1024,
                                TicketBucketConfig::setPauseDelayRuns,
                                TicketBucketConfig::pauseDelayRuns
                        )))
                        .then(intSetting(new IntSetting(
                                "wakeBatches",
                                1,
                                64,
                                TicketBucketConfig::setWakeBatches,
                                TicketBucketConfig::wakeBatches
                        )))
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boolSetting(BoolSetting setting) {
        return net.minecraft.commands.Commands.literal(setting.name())
                .executes(context -> {
                    sendReport(context.getSource(), setting.label(), setting.valueSupplier());
                    return 1;
                })
                .then(net.minecraft.commands.Commands.argument(setting.name(), BoolArgumentType.bool())
                        .suggests((context1, builder) -> SharedSuggestionProvider.suggest(List.of("true", "false"), builder))
                        .executes(context -> {
                            setting.setter().accept(BoolArgumentType.getBool(context, setting.name()));
                            TicketBucketConfig.save();
                            setting.afterSave().accept(context);
                            context.getSource().sendSuccess(
                                    () -> Component.literal(setting.label() + " set to " + setting.valueSupplier().get()),
                                    false
                            );
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> intSetting(IntSetting setting) {
        return net.minecraft.commands.Commands.literal(setting.name())
                .executes(context -> {
                    sendReport(context.getSource(), setting.name(), setting.valueSupplier());
                    return 1;
                })
                .then(net.minecraft.commands.Commands.argument(
	                                setting.name(),
	                                IntegerArgumentType.integer(setting.min(), setting.max())
	                        )
	                        .executes(context -> {
	                            setting.setter().accept(IntegerArgumentType.getInteger(context, setting.name()));
	                            TicketBucketConfig.save();
                            context.getSource().sendSuccess(
                                    () -> Component.literal(setting.name() + " set to " + setting.valueSupplier().get()),
                                    false
                            );
                            return 1;
                        }));
    }

    private static void sendReport(CommandSourceStack source, String label, Supplier<?> valueSupplier) {
        source.sendSuccess(() -> Component.literal(label + " = " + valueSupplier.get()), false);
    }

    private record BoolSetting(String name,
                               String label,
                               Consumer<Boolean> setter,
                               Supplier<Boolean> valueSupplier,
                               Consumer<CommandContext<CommandSourceStack>> afterSave) {
    }

    private record IntSetting(String name,
                              int min,
                              int max,
                              IntConsumer setter,
                              Supplier<Integer> valueSupplier) {
    }
}
