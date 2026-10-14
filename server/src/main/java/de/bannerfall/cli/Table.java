package de.bannerfall.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** A plain text table: columns as wide as their widest cell, numbers right-aligned. */
final class Table {

    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();

    Table(String... headers) {
        this.headers = List.of(headers);
    }

    Table row(Object... cells) {
        List<String> row = new ArrayList<>();
        for (Object cell : cells) {
            row.add(cell == null ? "–" : cell.toString());
        }
        rows.add(row);
        return this;
    }

    void print(PrintWriter out) {
        if (rows.isEmpty()) {
            out.println("(none)");
            return;
        }
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = headers.get(i).length();
            for (List<String> row : rows) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        line(out, headers, widths, false);
        for (List<String> row : rows) {
            line(out, row, widths, true);
        }
    }

    private static void line(PrintWriter out, List<String> cells, int[] widths, boolean align) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            boolean number = align && cell.matches("-?[0-9][0-9.,]*");
            String padded =
                    number
                            ? " ".repeat(widths[i] - cell.length()) + cell
                            : padRight(cell, widths[i]);
            line.append(i == 0 ? "" : "  ").append(padded);
        }
        out.println(line.toString().stripTrailing());
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(width - text.length());
    }
}
