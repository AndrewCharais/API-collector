package apicollector.ingest;

import java.util.*;
import java.util.regex.*;

/**
 * Interprets rows of cells into endpoints. CSV parsing and Apache POI (xlsx) both
 * produce List<List<String>> and feed this same interpreter.
 * Auto-detects a method column + path column from the header; falls back to a single
 * combined "GET /path" column, then to freeform per cell.
 */
public final class TabularImporter {
    private static final Pattern COMBINED = Pattern.compile(
        "^\\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+(/\\S*)", Pattern.CASE_INSENSITIVE);

    public List<ImportedEndpoint> fromRows(List<List<String>> rows, String source) {
        List<ImportedEndpoint> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return out;

        List<String> header = lower(rows.get(0));
        int methodCol = indexOfAny(header, "method", "verb", "http method", "httpmethod");
        int pathCol   = indexOfAny(header, "path", "endpoint", "url", "route", "uri", "resource");
        boolean hasHeader = methodCol >= 0 || pathCol >= 0;
        int start = hasHeader ? 1 : 0;

        for (int r = start; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.isEmpty()) continue;
            if (methodCol >= 0 && pathCol >= 0) {
                String m = cell(row, methodCol), p = cell(row, pathCol);
                if (!p.isBlank()) out.add(new ImportedEndpoint(blankToGet(m), p, source));
            } else if (pathCol >= 0) {
                addCombinedOrPath(out, cell(row, pathCol), source);
            } else {
                for (String c : row) addCombinedOrPath(out, c, source);
            }
        }
        return out;
    }

    private void addCombinedOrPath(List<ImportedEndpoint> out, String v, String source) {
        if (v == null || v.isBlank()) return;
        Matcher m = COMBINED.matcher(v);
        if (m.find()) out.add(new ImportedEndpoint(m.group(1), m.group(2), source));
        else if (v.trim().startsWith("/")) out.add(new ImportedEndpoint("GET", v.trim(), source));
    }

    /** Minimal RFC-4180-ish CSV splitter (handles quoted fields + embedded commas). */
    public static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : csv.split("\r?\n")) {
            if (line.isEmpty()) continue;
            List<String> cells = new ArrayList<>(); StringBuilder cur = new StringBuilder(); boolean q = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (q) {
                    if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else if (c == '"') q = false; else cur.append(c);
                } else {
                    if (c == '"') q = true;
                    else if (c == ',') { cells.add(cur.toString().trim()); cur.setLength(0); }
                    else cur.append(c);
                }
            }
            cells.add(cur.toString().trim());
            rows.add(cells);
        }
        return rows;
    }

    private static List<String> lower(List<String> r) { List<String> o = new ArrayList<>(); for (String s : r) o.add(s == null ? "" : s.toLowerCase().trim()); return o; }
    private static int indexOfAny(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) for (String n : names) if (header.get(i).equals(n)) return i;
        return -1;
    }
    private static String cell(List<String> row, int i) { return i >= 0 && i < row.size() ? row.get(i) : ""; }
    private static String blankToGet(String m) { return m == null || m.isBlank() ? "GET" : m; }
}
