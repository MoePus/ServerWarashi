package com.moepus.serverwarashi.common.modules.idlefreeze;

import com.moepus.serverwarashi.common.config.IdleFreezeConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * IdleFreeze 模块运行时协调入口。
 * 负责事件驱动状态和玩家访问跟踪，不承载冻结判定本体。
 */
public final class IdleFreezeRuntime {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private IdleFreezeRuntime() {
    }

    public static void onServerStarted(MinecraftServer server) {
        // 模块禁用则不做任何事
        if (IdleFreezeConfig.disabled()) {
            return;
        }
        // 先重放各维度已存盘的 IDLE 冻结，保证重启后立即恢复暂停
        for (var level : server.getAllLevels()) {
            IdleFreezeService.reapplySavedIdlePause(level);
        }
        // 延迟一段时间后再做首次完整扫描（等世界/区块稳定）
        int delay = IdleFreezeConfig.initialScanDelayTicks();
        server.tell(new TickTask(server.getTickCount() + delay, () -> {
            if (IdleFreezeConfig.disabled()) {
                return;
            }
            for (var level : server.getAllLevels()) {
                IdleFreezeService.runInitialScan(level);
            }
        }));
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        // 模块禁用则不跟踪访问
        if (IdleFreezeConfig.disabled()) {
            return;
        }
        // 每 CHECK_INTERVAL_TICKS 才处理一次，降低开销
        int tick = player.getServer().getTickCount();
        if (tick % CHECK_INTERVAL_TICKS == 0) {
            long chunkPos = player.chunkPosition().toLong();
            ServerLevel level = player.serverLevel();
            // 刷新玩家所在 chunk 的最近访问日期
            IdleVisitData.get(level).markSeen(chunkPos, IdleFreezeService.currentEpochDay());
            // 若该 chunk 属于冻结 owner，则解冻该 owner 当前覆盖的 chunks
            IdleFreezeService.tryUnfreezeByChunk(level, chunkPos);
        }
    }
}
