package com.moepus.serverwarashi.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.moepus.serverwarashi.common.modules.idlefreeze.IdleFreezeService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
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
        root.then(net.minecraft.commands.Commands.literal("idlefreeze")
                .then(net.minecraft.commands.Commands.literal("force")
                        .executes(context -> {
                            var source = context.getSource();
                            source.sendSuccess(() -> {
                                // 清访问记录 + 立即重扫，再回绿色完成提示
                                IdleFreezeService.forceRescan(source.getLevel());
                                return Component.literal("Idle-freeze force scan finished.")
                                        .withStyle(ChatFormatting.GREEN);
                            }, false);
                            return 1;
                        })
                )
        );
    }
}
