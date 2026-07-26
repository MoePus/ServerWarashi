package com.moepus.serverwarashi.common.modules;

import com.moepus.serverwarashi.common.modules.performance.sampling.PerfWorkTracker;
import com.moepus.serverwarashi.common.modules.performance.sampling.TickType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class TickTimingHooks {
    private TickTimingHooks() {
    }

    public static void beforeChunk(ServerLevel level, LevelChunk chunk) {
        PerfWorkTracker.enterChunk(level, chunk);
    }

    public static void afterChunk() {
        PerfWorkTracker.exit(TickType.CHUNK);
    }

    public static void beforeEntity(ServerLevel level, Entity entity) {
        PerfWorkTracker.enterEntity(level, entity);
    }

    public static void afterEntity() {
        PerfWorkTracker.exit(TickType.ENTITY);
    }

    public static void beforeBlockEntity(BlockEntity blockEntity) {
        PerfWorkTracker.enterBlockEntity(blockEntity);
    }

    public static void afterBlockEntity() {
        PerfWorkTracker.exit(TickType.BLOCK_ENTITY);
    }

    public static void beforeTask(BlockableEventLoop<?> eventLoop, Runnable task) {
        PerfWorkTracker.enterTask(eventLoop, task);
    }

    public static void afterTask() {
        PerfWorkTracker.exit(TickType.TASK);
    }
}
