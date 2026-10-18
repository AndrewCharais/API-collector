package apicollector.core;

import apicollector.export.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a JSON body into leaf field-name -> value pairs (last-writer-wins on repeats),
 * which is how the Known Values registry keys things. Used for request AND response bodies.
 */
public final class JsonFlattener {
    public Map<String, String> flatten(String jsonBody) {
        Map<String, String> out = new LinkedHashMap<>();
        if (jsonBody == null || jsonBody.isBlank()) return out;
        try { walk(null, Json.read(jsonBody), out); } catch (RuntimeException ignore) { /* not JSON */ }
        return out;
    }
    private void walk(String key, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) walk(String.valueOf(e.getKey()), e.getValue(), out);
        } else if (node instanceof List<?> l) {
            for (Object it : l) walk(key, it, out); // array elements keep the field name
        } else if (key != null && node != null) {
            out.put(key, scalar(node));
        }
    }
    private static String scalar(Object o) {
        if (o instanceof Double d && d == Math.floor(d) && !d.isInfinite())
            return String.valueOf(d.longValue());
        return String.valueOf(o);
    }
}
