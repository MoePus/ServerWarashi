package com.moepus.serverwarashi.common.report;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public final class TableReportCodec {
    private static final StreamCodec<RegistryFriendlyByteBuf, String> STRING_CODEC =
            ByteBufCodecs.STRING_UTF8.mapStream(buffer -> buffer);
    public static final StreamCodec<RegistryFriendlyByteBuf, TableReport> STREAM_CODEC = StreamCodec.composite(
            STRING_CODEC,
            TableReport::title,
            STRING_CODEC.apply(ByteBufCodecs.list()),
            TableReport::metadata,
            StreamCodec.of(TableReportCodec::writeSection, TableReportCodec::readSection).apply(ByteBufCodecs.list()),
            TableReport::sections,
            TableReport::new
    );

    private static final StreamCodec<RegistryFriendlyByteBuf, TableReport.Button> BUTTON_CODEC = StreamCodec.composite(
            STRING_CODEC,
            TableReport.Button::label,
            STRING_CODEC,
            TableReport.Button::command,
            TableReport.Button::new
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRING_ROW_CODEC =
            STRING_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<List<String>>> ROWS_CODEC =
            STRING_ROW_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<TableReport.Button>> BUTTON_ROW_CODEC =
            BUTTON_CODEC.apply(ByteBufCodecs.list());

    private TableReportCodec() {
    }

    private static TableReport.Section readSection(RegistryFriendlyByteBuf buffer) {
        String title = STRING_CODEC.decode(buffer);
        List<String> columns = STRING_ROW_CODEC.decode(buffer);
        List<List<String>> rows = ROWS_CODEC.decode(buffer);
        List<List<TableReport.Button>> rowButtons = readRowButtons(buffer, rows.size());
        return TableReport.section(title, columns, rows, rowButtons);
    }

    private static void writeSection(RegistryFriendlyByteBuf buffer, TableReport.Section section) {
        STRING_CODEC.encode(buffer, section.title());
        STRING_ROW_CODEC.encode(buffer, section.columns());
        ROWS_CODEC.encode(buffer, section.rows());
        writeRowButtons(buffer, section.rowButtons());
    }

    private static List<List<TableReport.Button>> readRowButtons(RegistryFriendlyByteBuf buffer, int expectedRows) {
        // 0 行表示无按钮；否则按钮行数必须等于数据行数
        int rowCount = readCount(buffer);
        if (rowCount == 0) {
            return List.of();
        }
        if (rowCount != expectedRows) {
            throw new IllegalArgumentException("rowButtons must be empty or match rows size");
        }
        List<List<TableReport.Button>> rows = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            rows.add(BUTTON_ROW_CODEC.decode(buffer));
        }
        return rows;
    }

    private static void writeRowButtons(RegistryFriendlyByteBuf buffer, List<List<TableReport.Button>> rowButtons) {
        // 先写总行数，再逐行写入该行的按钮列表（每行先写按钮数，再写各按钮内容）
        buffer.writeVarInt(rowButtons.size());
        for (List<TableReport.Button> row : rowButtons) {
            BUTTON_ROW_CODEC.encode(buffer, row);
        }
    }

    private static int readCount(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0) {
            throw new IllegalArgumentException("button rows count cannot be negative: " + count);
        }
        return count;
    }
}
