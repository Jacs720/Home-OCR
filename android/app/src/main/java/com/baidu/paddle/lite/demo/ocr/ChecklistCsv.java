package com.baidu.paddle.lite.demo.ocr;

import java.util.ArrayList;
import java.util.List;

public final class ChecklistCsv {
    private ChecklistCsv() {
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String source = text == null ? "" : text;

        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < source.length() && source.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((value == '\r' || value == '\n') && !quoted) {
                if (value == '\r' && index + 1 < source.length()
                        && source.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                if (!isEmptyRow(row)) rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(value);
            }
        }

        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            if (!isEmptyRow(row)) rows.add(row);
        }
        if (!rows.isEmpty() && !rows.get(0).isEmpty()) {
            String first = rows.get(0).get(0);
            if (!first.isEmpty() && first.charAt(0) == '\uFEFF') {
                rows.get(0).set(0, first.substring(1));
            }
        }
        return rows;
    }

    private static boolean isEmptyRow(List<String> row) {
        for (String cell : row) {
            if (!cell.isEmpty()) return false;
        }
        return true;
    }
}
