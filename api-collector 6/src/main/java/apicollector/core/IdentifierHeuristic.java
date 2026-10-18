package apicollector.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Conservative "is this path segment an identifier?" test. Bias is toward LITERAL:
 * a segment is only treated as an id when it clearly looks like one, so real route
 * words (users, v1, me, search) are never collapsed.
 */
public final class IdentifierHeuristic {
    private static final Pattern NUMERIC   = Pattern.compile("^\\d+$");
    private static final Pattern UUID      = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern HEX32     = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern OBJECTID  = Pattern.compile("^[0-9a-fA-F]{24}$");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern TOKENISH  = Pattern.compile("^[A-Za-z0-9_.~-]+$");

    private int longTokenMinLength = 16;

    public IdentifierHeuristic() {}
    public IdentifierHeuristic longTokenMinLength(int n) { this.longTokenMinLength = n; return this; }

    public boolean looksLikeIdentifier(String segment) {
        if (segment == null || segment.isEmpty()) return false;
        String s = decode(segment);
        if (NUMERIC.matcher(s).matches())  return true;
        if (UUID.matcher(s).matches())     return true;
        if (HEX32.matcher(s).matches())    return true;
        if (OBJECTID.matcher(s).matches()) return true;
        return s.length() >= longTokenMinLength
            && HAS_DIGIT.matcher(s).matches()
            && TOKENISH.matcher(s).matches();
    }

    private static String decode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }
}
