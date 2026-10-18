package apicollector.core;

import apicollector.export.PlaceholderStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a user-entered list of placeholder formats, e.g. "{{name}}, :name, ${name}".
 * Live auto-fill matches ALL configured formats, because a client collection may mix them
 * and the format you match is not necessarily the format you export.
 */
public final class PlaceholderFormats {
    private PlaceholderFormats() {}

    /** Split on commas/newlines; each entry must contain the literal word "name". */
    public static List<PlaceholderStyle> parse(String spec) {
        List<PlaceholderStyle> out = new ArrayList<>();
        if (spec == null) return out;
        for (String raw : spec.split("[,\\n]")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            int i = t.indexOf("name");
            if (i < 0) continue;                       // ignore malformed entries
            out.add(new PlaceholderStyle(t.substring(0, i), t.substring(i + 4)));
        }
        return out;
    }

    public static String describe(List<PlaceholderStyle> styles) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < styles.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(styles.get(i).wrap("name"));
        }
        return sb.toString();
    }
}
