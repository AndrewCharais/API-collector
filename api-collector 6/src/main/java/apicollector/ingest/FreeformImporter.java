package apicollector.ingest;

import java.util.*;
import java.util.regex.*;

/** Extracts "METHOD /path" pairs from arbitrary pasted text (docs, tickets, notes). */
public final class FreeformImporter {
    private static final Pattern MP = Pattern.compile(
        "(?im)\\b(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\b[\\s:|-]+(/[^\\s\"'<>]*)");

    public List<ImportedEndpoint> parse(String text, String source) {
        List<ImportedEndpoint> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = MP.matcher(text);
        while (m.find()) out.add(new ImportedEndpoint(m.group(1), trim(m.group(2)), source));
        return out;
    }

    /** Drop trailing sentence punctuation but keep a closing brace from {id}. */
    private static String trim(String p) {
        int end = p.length();
        while (end > 0 && ".,;:)]".indexOf(p.charAt(end - 1)) >= 0) end--;
        return p.substring(0, end);
    }
}
