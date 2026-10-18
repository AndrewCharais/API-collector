package apicollector.core;

import apicollector.export.PlaceholderStyle;

import java.util.*;

/**
 * On-the-wire placeholder substitution for API-only testing (no web UI to walk).
 * You fire a request containing {{orderId}} from Postman/Repeater; this swaps in the value
 * captured from an earlier response before the request leaves Burp.
 *
 * Pure string work so it is testable without Burp. Content-Length is recalculated because
 * substitution changes the body length.
 */
public final class LiveSubstitution {
    private final PlaceholderHydrator hydrator = new PlaceholderHydrator();

    public static final class Outcome {
        public final String raw;                 // rewritten raw request
        public final boolean changed;
        public final Set<String> filled;         // placeholder names substituted
        public final Set<String> missing;        // placeholders with no value yet
        Outcome(String raw, boolean changed, Set<String> filled, Set<String> missing) {
            this.raw = raw; this.changed = changed; this.filled = filled; this.missing = missing;
        }
    }

    /** Substitute placeholders throughout a raw HTTP request, fixing Content-Length after. */
    public Outcome apply(String rawRequest, PlaceholderStyle style, Map<String, String> values) {
        if (rawRequest == null || rawRequest.isEmpty())
            return new Outcome(rawRequest, false, Set.of(), Set.of());

        PlaceholderHydrator.Result r = hydrator.hydrate(rawRequest, style, values);
        if (r.replacements == 0)
            return new Outcome(rawRequest, false, r.resolved, r.unresolved);

        String fixed = fixContentLength(r.text);
        return new Outcome(fixed, true, r.resolved, r.unresolved);
    }

    /** Recompute Content-Length from the actual body (substitution changes its size). */
    static String fixContentLength(String raw) {
        int sep = indexOfBodySeparator(raw);
        if (sep < 0) return raw;                       // no body
        String head = raw.substring(0, sep);
        String body = raw.substring(sep + separatorLength(raw, sep));
        int len = body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : head.split("\r?\n", -1)) {
            if (!replaced && line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                out.append("Content-Length: ").append(len);
                replaced = true;
            } else out.append(line);
            out.append("\r\n");
        }
        // head already ends with a line terminator from the loop; add blank line + body
        return out.append("\r\n").append(body).toString();
    }

    private static int indexOfBodySeparator(String raw) {
        int i = raw.indexOf("\r\n\r\n");
        if (i >= 0) return i;
        return raw.indexOf("\n\n");
    }
    private static int separatorLength(String raw, int idx) {
        return raw.startsWith("\r\n\r\n", idx) ? 4 : 2;
    }
}
