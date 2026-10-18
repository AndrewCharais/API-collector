package apicollector.ingest;

import java.util.*;

/**
 * Reads the paths{} object of an OpenAPI 3.x or Swagger 2.0 document.
 * Takes an already-decoded Map, so JSON (via Json.read) and YAML (via SnakeYAML/Jackson)
 * share this exact interpreter. Applies basePath (Swagger 2) or the first server path (OAI 3).
 */
public final class OpenApiImporter {
    private static final Set<String> METHODS =
        Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

    @SuppressWarnings("unchecked")
    public List<ImportedEndpoint> fromMap(Map<String, Object> doc, String source) {
        List<ImportedEndpoint> out = new ArrayList<>();
        if (doc == null) return out;
        String prefix = prefix(doc);
        Object pathsObj = doc.get("paths");
        if (!(pathsObj instanceof Map)) return out;

        for (Map.Entry<String, Object> pe : ((Map<String, Object>) pathsObj).entrySet()) {
            String path = prefix + pe.getKey();
            if (!(pe.getValue() instanceof Map)) continue;
            for (Map.Entry<String, Object> me : ((Map<String, Object>) pe.getValue()).entrySet()) {
                if (METHODS.contains(me.getKey().toLowerCase()))
                    out.add(new ImportedEndpoint(me.getKey(), path, source));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private String prefix(Map<String, Object> doc) {
        Object bp = doc.get("basePath");                       // Swagger 2.0
        if (bp instanceof String s && !s.isBlank()) return trimTrailingSlash(s);
        Object servers = doc.get("servers");                   // OpenAPI 3.x
        if (servers instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> s0) {
            Object url = ((Map<String, Object>) s0).get("url");
            if (url instanceof String u) return trimTrailingSlash(pathOf(u));
        }
        return "";
    }
    private static String pathOf(String url) {
        int scheme = url.indexOf("://");
        int slash = url.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
        return slash >= 0 ? url.substring(slash) : "";
    }
    private static String trimTrailingSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }
}
