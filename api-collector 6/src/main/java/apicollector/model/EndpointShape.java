package apicollector.model;

import java.util.List;
import java.util.Objects;

/**
 * Normalized (method + path) shape. This is the single join key used for BOTH
 * endpoint grouping and coverage matching, so grouping and coverage can never
 * disagree about what "the same endpoint" means. Params collapse to {@link #PARAM}
 * so /users/123 and /users/456 produce an equal shape.
 */
public final class EndpointShape {
    public static final String PARAM = "{}";

    private final String method;
    private final List<String> segments;

    public EndpointShape(String method, List<String> segments) {
        this.method = method == null ? "" : method.toUpperCase();
        this.segments = List.copyOf(segments);
    }

    public String method() { return method; }
    public List<String> segments() { return segments; }

    public String canonical() { return method + " /" + String.join("/", segments); }

    public String displayTemplate() {
        StringBuilder sb = new StringBuilder("/");
        int p = 0;
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append('/');
            String s = segments.get(i);
            if (s.equals(PARAM)) { p++; sb.append(p == 1 ? "{id}" : "{id" + p + "}"); }
            else sb.append(s);
        }
        return sb.toString();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EndpointShape e)) return false;
        return method.equals(e.method) && segments.equals(e.segments);
    }
    @Override public int hashCode() { return Objects.hash(method, segments); }
    @Override public String toString() { return canonical(); }
}
