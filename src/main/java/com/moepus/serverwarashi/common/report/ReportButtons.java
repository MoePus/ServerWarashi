package com.moepus.serverwarashi.common.report;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class ReportButtons {
    private ReportButtons() {
    }

    public static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "";
        }
        return String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    public static String teleportCommand(ResourceLocation dimension, BlockPos pos) {
        return teleportCommand(dimension.toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static String teleportCommand(String dimension, int x, int y, int z) {
        return String.format("execute in %s run tp @s %d %d %d", dimension, x, y, z);
    }

    public static String chunkTeleportCommand(String dimension, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        return String.format("execute in %s run tp @s %d ~ %d", dimension, x, z);
    }
}
