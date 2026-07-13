package com.moepus.serverwarashi.common.report;

import java.util.ArrayList;
import java.util.List;

public record TableReport(String title, List<String> metadata, List<Section> sections) {
    public TableReport {
        // 防御性拷贝为不可变列表，避免外部后续改动
        metadata = List.copyOf(metadata);
        sections = List.copyOf(sections);
    }

    public record Button(String label, String command) {
    }

    public record Section(String title,
                          List<String> columns,
                          List<List<String>> rows,
                          List<List<Button>> rowButtons) {
        public Section {
            // 深拷贝为不可变结构，并校验行内按钮数量与行数一致（或为空）
            columns = List.copyOf(columns);
            rows = deepCopy(rows);
            rowButtons = deepCopy(rowButtons);
            if (!rowButtons.isEmpty() && rowButtons.size() != rows.size()) {
                throw new IllegalArgumentException("rowButtons must be empty or match rows size");
            }
        }

    }

    public static Section section(String title,
                                  List<String> columns,
                                  List<List<String>> rows,
                                  List<List<Button>> rowButtons) {
        return new Section(title, columns, rows, rowButtons);
    }

    private static <T> List<List<T>> deepCopy(List<List<T>> rows) {
        // 逐行拷贝为不可变列表
        List<List<T>> copy = new ArrayList<>(rows.size());
        for (List<T> row : rows) {
            copy.add(List.copyOf(row));
        }
        return List.copyOf(copy);
    }
}
