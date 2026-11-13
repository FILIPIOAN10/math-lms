package ro.mathlms.gradebook;

import java.util.List;

/**
 * The few CSV rules a file opened in a Romanian Excel needs: ";" between cells (the comma is the decimal sign there),
 * CRLF line ends, quotes around a cell that holds a separator, a quote or a line break, and a byte-order mark first
 * so "ș" and "ț" are read as UTF-8.
 */
final class Csv {

    static final String SEPARATOR = ";";
    static final String BOM = "﻿";

    private Csv() {
    }

    /**
     * A cell of free text - a name, a title, anything a person typed. A spreadsheet runs a cell that starts with
     * {@code = + - @} as a formula ("CSV injection": a student who registers as {@code =HYPERLINK(...)} would run it in
     * the teacher's Excel), so such a value gets a leading apostrophe and is shown as plain text.
     */
    static String text(String value) {
        String cell = value == null ? "" : value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        boolean quote = cell.contains(SEPARATOR) || cell.contains("\"") || cell.contains("\n") || cell.contains("\r");
        return quote ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }

    /** One line of cells that are already safe ({@link #text}, or a number we formatted ourselves). */
    static String line(List<String> cells) {
        return String.join(SEPARATOR, cells) + "\r\n";
    }
}
