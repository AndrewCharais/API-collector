package apicollector.core;

import apicollector.export.PlaceholderStyle;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills placeholders in an arbitrary collection/text with known values (the inverse of
 * export). Text-level match-and-replace, so it works on any format (Postman JSON, Bruno,
 * raw requests). A placeholder whose name isn't known is left untouched and reported.
 */
public final class PlaceholderHydrator {
    private static final String NAME = "([A-Za-z0-9_.\\-]+)";

    public static final class Result {
        public final String text;
        public final int replacements;                                // total tokens replaced
        public final Set<String> resolved   = new LinkedHashSet<>();  // distinct names filled
        public final Set<String> unresolved = new LinkedHashSet<>();  // distinct names left as-is
        Result(String text, int replacements) { this.text = text; this.replacements = replacements; }
    }

    public Result hydrate(String text, PlaceholderStyle style, Map<String, String> values) {
        if (text == null) return new Result("", 0);
        Matcher m = compile(style).matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        Set<String> resolved = new LinkedHashSet<>(), unresolved = new LinkedHashSet<>();
        while (m.find()) {
            String name = m.group(1);
            String val = values.get(name);
            if (val != null) { m.appendReplacement(out, Matcher.quoteReplacement(val)); count++; resolved.add(name); }
            else            { m.appendReplacement(out, Matcher.quoteReplacement(m.group())); unresolved.add(name); }
        }
        m.appendTail(out);
        Result r = new Result(out.toString(), count);
        r.resolved.addAll(resolved);
        r.unresolved.addAll(unresolved);
        return r;
    }

    private static Pattern compile(PlaceholderStyle style) {
        String pfx = Pattern.quote(style.prefix);
        String sfx = style.suffix.isEmpty() ? "" : Pattern.quote(style.suffix);
        return Pattern.compile(pfx + NAME + sfx);
    }
}
