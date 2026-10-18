package apicollector.core;

import apicollector.export.PlaceholderStyle;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A client-provided collection held in memory while testing. Its placeholders are the
 * "shopping list" of values to capture; as traffic is walked, they resolve. Format-agnostic
 * (Postman JSON, Bruno, raw dumps) because matching is text-level.
 */
public final class LoadedCollection {
    private static final String NAME = "([A-Za-z0-9_.\\-]+)";

    private final String fileName;
    private final String text;
    private final PlaceholderStyle style;
    private final List<String> placeholders;   // distinct, in first-seen order
    private final int tokenCount;              // total occurrences

    public LoadedCollection(String fileName, String text, PlaceholderStyle style) {
        this.fileName = fileName == null ? "collection" : fileName;
        this.text = text == null ? "" : text;
        this.style = style;
        LinkedHashSet<String> found = new LinkedHashSet<>();
        int n = 0;
        Matcher m = pattern(style).matcher(this.text);
        while (m.find()) { found.add(m.group(1)); n++; }
        this.placeholders = new ArrayList<>(found);
        this.tokenCount = n;
    }

    public String fileName() { return fileName; }
    public String text() { return text; }
    public PlaceholderStyle style() { return style; }
    public List<String> placeholderNames() { return Collections.unmodifiableList(placeholders); }
    public int distinctPlaceholders() { return placeholders.size(); }
    public int tokenCount() { return tokenCount; }

    /** Placeholder names that have no value yet in the registry. */
    public List<String> unresolved(ParameterRegistry reg) {
        List<String> out = new ArrayList<>();
        for (String p : placeholders) if (reg.active(p) == null) out.add(p);
        return out;
    }
    public int resolvedCount(ParameterRegistry reg) {
        return placeholders.size() - unresolved(reg).size();
    }

    static Pattern pattern(PlaceholderStyle style) {
        String pfx = Pattern.quote(style.prefix);
        String sfx = style.suffix.isEmpty() ? "" : Pattern.quote(style.suffix);
        return Pattern.compile(pfx + NAME + sfx);
    }
}
