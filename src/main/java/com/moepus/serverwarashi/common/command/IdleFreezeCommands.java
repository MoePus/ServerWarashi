package com.moepus.serverwarashi.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moepus.serverwarashi.common.modules.idlefreeze.IdleFreezeService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 注册 idlefreeze 模块命令。
 */
final class IdleFreezeCommands {
    private IdleFreezeCommands() {
    }

    /**
     * 将 {@code /warashi idlefreeze ...} 命令挂载到根节点。
     *
     * @param root 根命令节点
     */
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        // /warashi idlefreeze force：立即对当前维度强制跑一次冻结扫描并回显结果
        root.then(Commands.literal("idlefreeze")
                .then(Commands.literal("force")
                        .executes(context -> {
                            var source = context.getSource();
                            // 清访问记录 + 立即重扫；副作用须在 sendSuccess 之外执行——
                            // 命令反馈被抑制（sendCommandFeedback=false / silent execute）时 Supplier 不会被调用
                            IdleFreezeService.forceRescan(source.getLevel());
                            source.sendSuccess(() -> Component.literal("Idle-freeze force scan finished.")
                                    .withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        })
                )
        );
    }
}
