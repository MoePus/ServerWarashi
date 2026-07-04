package com.moepus.serverwarashi.common.report;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用报告表格的坐标格式化与行内按钮装配（与具体功能模块无关）。
 * 模块特有的命令按钮（如 perf 的分析按钮）由调用方注入到 {@link #locate} 的 extra 参数。
 */
public final class ReportButtons {
    private ReportButtons() {
    }

    public static String formatPos(BlockPos pos) {
        // 坐标格式化；null 返回空串
        if (pos == null) {
            return "";
        }
        return String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    public static TableReport.Button teleport(ResourceLocation dimension, BlockPos pos) {
        // 坐标为空则无传送按钮
        if (pos == null) {
            return null;
        }
        return new TableReport.Button("TP", teleportCommand(dimension, pos));
    }

    public static String teleportCommand(ResourceLocation dimension, BlockPos pos) {
        return String.format(
                "execute in %s run tp @s %d %d %d",
                dimension, pos.getX(), pos.getY(), pos.getZ()
        );
    }

    /**
     * 装配行内按钮：传送按钮（按 tpTarget）+ 调用方注入的额外按钮（null 自动略过）。
     */
    public static List<TableReport.Button> locate(ResourceLocation dimension,
                                                  BlockPos tpTarget,
                                                  TableReport.Button... extra) {
        List<TableReport.Button> buttons = new ArrayList<>(1 + extra.length);
        TableReport.Button teleport = teleport(dimension, tpTarget);
        if (teleport != null) {
            buttons.add(teleport);
        }
        for (TableReport.Button button : extra) {
            if (button != null) {
                buttons.add(button);
            }
        }
        return List.copyOf(buttons);
    }
}
