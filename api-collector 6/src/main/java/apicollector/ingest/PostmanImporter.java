package apicollector.ingest;

import java.util.*;

/** Walks a Postman v2.1 collection's item tree (folders + requests). Handles "empty" collections. */
public final class PostmanImporter {
    @SuppressWarnings("unchecked")
    public List<ImportedEndpoint> fromMap(Map<String, Object> doc, String source) {
        List<ImportedEndpoint> out = new ArrayList<>();
        if (doc == null) return out;
        // Postman's API export wraps the collection: { "collection": { "info":..., "item":... } }
        Object inner = doc.get("collection");
        if (doc.get("item") == null && inner instanceof Map)
            doc = (Map<String, Object>) inner;
        walk(doc.get("item"), out, source);
        return out;
    }
    @SuppressWarnings("unchecked")
    private void walk(Object items, List<ImportedEndpoint> out, String source) {
        if (!(items instanceof List)) return;
        for (Object o : (List<Object>) items) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) o;
            if (item.get("item") != null) { walk(item.get("item"), out, source); continue; } // folder
            Object req = item.get("request");
            if (!(req instanceof Map)) continue;
            Map<String, Object> r = (Map<String, Object>) req;
            String method = str(r.get("method"), "GET");
            String path = extractPath(r.get("url"));
            if (path != null) out.add(new ImportedEndpoint(method, path, source));
        }
    }
    @SuppressWarnings("unchecked")
    private String extractPath(Object url) {
        if (url instanceof String s) return pathFromRaw(s);
        if (url instanceof Map) {
            Map<String, Object> u = (Map<String, Object>) url;
            Object parts = u.get("path");
            if (parts instanceof List<?> l && !l.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Object p : l) sb.append('/').append(String.valueOf(p));
                return sb.toString();
            }
            if (u.get("raw") instanceof String raw) return pathFromRaw(raw);
        }
        return null;
    }
    private String pathFromRaw(String raw) {
        String s = raw;
        int q = s.indexOf('?'); if (q >= 0) s = s.substring(0, q);
        if (s.startsWith("{{")) { int c = s.indexOf("}}"); if (c >= 0) { int sl = s.indexOf('/', c); return sl >= 0 ? s.substring(sl) : "/"; } }
        int scheme = s.indexOf("://");
        int slash = s.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
        return slash >= 0 ? s.substring(slash) : (s.startsWith("/") ? s : "/" + s);
    }
    private static String str(Object o, String def) { return o == null ? def : o.toString(); }
}
