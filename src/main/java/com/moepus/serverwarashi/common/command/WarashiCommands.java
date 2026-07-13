package com.moepus.serverwarashi.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.function.Predicate;

/**
 * Warashi 根命令装配器。
 * 层级：命令装配层。
 * 上游：ServerWarashi。下游：各命令入口。
 */
public final class WarashiCommands {
    private static final Predicate<CommandSourceStack> ADMIN_PERMISSION =
            source -> source.hasPermission(2);

    private WarashiCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 建根节点 /warashi，并要求 OP 权限等级 2
        var root = Commands.literal("warashi")
                .requires(ADMIN_PERMISSION);
        // 依次把各模块子命令挂载到根节点下
        TicketBucketCommands.register(root);
        TicketPerfCommands.register(root);
        IdleFreezeCommands.register(root);
        // 把装配好的根节点注册进全局命令派发器
        dispatcher.register(root);
    }
}
