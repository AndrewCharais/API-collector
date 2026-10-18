package apicollector.core;

import apicollector.model.EndpointShape;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete request path -> {@link EndpointShape}.
 * shapeObserved: captured traffic (id heuristic only).
 * shapeExpected: spec paths, ALSO collapsing explicit placeholders ({id}, :id, {{id}})
 *                so spec-vs-traffic naming differences can't cause false-negative coverage.
 */
public final class PathNormalizer {
    private final IdentifierHeuristic heuristic;

    public PathNormalizer() { this(new IdentifierHeuristic()); }
    public PathNormalizer(IdentifierHeuristic h) { this.heuristic = h; }

    public EndpointShape shapeObserved(String method, String rawPath) { return shape(method, rawPath, false); }
    public EndpointShape shapeExpected(String method, String rawPath) { return shape(method, rawPath, true); }

    private EndpointShape shape(String method, String rawPath, boolean allowExplicit) {
        List<String> out = new ArrayList<>();
        for (String seg : split(strip(rawPath))) {
            if (allowExplicit && isExplicitPlaceholder(seg)) out.add(EndpointShape.PARAM);
            else if (heuristic.looksLikeIdentifier(seg))     out.add(EndpointShape.PARAM);
            else                                             out.add(seg);
        }
        return new EndpointShape(method, out);
    }

    private static boolean isExplicitPlaceholder(String s) {
        if (s.length() >= 2 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}') return true;
        return s.length() >= 2 && s.charAt(0) == ':';
    }
    private static String strip(String p) {
        if (p == null) return "";
        int q = p.indexOf('?'); if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#'); if (h >= 0) p = p.substring(0, h);
        return p;
    }
    private static List<String> split(String p) {
        List<String> out = new ArrayList<>();
        for (String s : p.split("/")) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
