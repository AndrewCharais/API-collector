package apicollector.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Bruno .bru request files. Bruno is a text format, not JSON: the HTTP method is the
 * name of the block that holds the url, e.g.
 *
 *   put {
 *     url: {{baseUrl}}/api/orders/{{orderId}}/status
 *   }
 */
public final class BrunoImporter {
    private static final Set<String> METHODS =
        Set.of("get", "post", "put", "patch", "delete", "head", "options");

    /** method block header, e.g. "put {" at the start of a line */
    private static final Pattern BLOCK = Pattern.compile("(?m)^\\s*([a-zA-Z]+)\\s*\\{");
    private static final Pattern URL   = Pattern.compile("(?m)^\\s*url\\s*:\\s*(\\S+)");

    public List<ImportedEndpoint> parse(String bru, String source) {
        List<ImportedEndpoint> out = new ArrayList<>();
        if (bru == null || bru.isBlank()) return out;

        Matcher b = BLOCK.matcher(bru);
        while (b.find()) {
            String method = b.group(1).toLowerCase(Locale.ROOT);
            if (!METHODS.contains(method)) continue;           // skip meta/headers/body/etc.
            int blockEnd = matchingBrace(bru, b.end() - 1);
            String block = bru.substring(b.end(), blockEnd < 0 ? bru.length() : blockEnd);
            Matcher u = URL.matcher(block);
            if (u.find()) {
                String path = pathOf(u.group(1));
                if (path != null) out.add(new ImportedEndpoint(method, path, source));
            }
        }
        return out;
    }

    /** Strip scheme/host or a leading {{baseUrl}}-style variable, and any query string. */
    static String pathOf(String url) {
        String s = url.trim();
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        if (s.startsWith("{{")) {                       // {{baseUrl}}/api/x
            int close = s.indexOf("}}");
            if (close >= 0) { int slash = s.indexOf('/', close); return slash >= 0 ? s.substring(slash) : "/"; }
        }
        int scheme = s.indexOf("://");
        int slash = s.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
        if (slash >= 0) return s.substring(slash);
        return s.startsWith("/") ? s : null;
    }

    /** Index of the brace closing the one at openIdx, honouring nesting (Bruno bodies nest braces). */
    private static int matchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }
}
