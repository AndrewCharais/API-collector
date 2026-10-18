package apicollector.export;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bruno collection: bruno.json + one .bru per request + environments/<env>.bru.
 * Deliberately a very different on-disk format from Postman, to exercise the IR.
 */
public final class BrunoExporter implements CollectionExporter {
    @Override public String id() { return "bruno"; }

    @Override public Map<String, String> export(Collection c, EnvironmentModel env, String baseName) {
        String root = slug(baseName == null || baseName.isBlank() ? c.name : baseName);
        Map<String, String> out = new LinkedHashMap<>();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("version", "1");
        meta.put("name", baseName == null || baseName.isBlank() ? c.name : baseName);
        meta.put("type", "collection");
        out.put(root + "/bruno.json", Json.write(meta));

        int seq = 1;
        for (Collection.Folder f : c.folders) {
            for (Collection.Request r : f.requests) {
                String dir = folderSlug(f.name);
                out.put(root + "/" + dir + "/" + requestSlug(r.name) + ".bru", bru(r, seq++));
            }
        }
        if (env != null) out.put(root + "/environments/" + slug(env.name) + ".bru", envBru(env));
        return out;
    }

    private String bru(Collection.Request r, int seq) {
        StringBuilder b = new StringBuilder();
        b.append("meta {\n")
         .append("  name: ").append(r.name).append("\n")
         .append("  type: http\n")
         .append("  seq: ").append(seq).append("\n}\n\n");

        String method = r.method.toLowerCase();
        b.append(method).append(" {\n")
         .append("  url: ").append(fullUrl(r)).append("\n")
         .append("  body: ").append("raw".equals(r.bodyMode) ? r.bodyLanguage : "none").append("\n")
         .append("  auth: none\n}\n\n");

        if (!r.headers.isEmpty()) {
            b.append("headers {\n");
            for (String[] h : r.headers) b.append("  ").append(h[0]).append(": ").append(h[1]).append("\n");
            b.append("}\n\n");
        }
        if (!r.query.isEmpty()) {
            b.append("params:query {\n");
            for (String[] q : r.query) b.append("  ").append(q[0]).append(": ").append(q[1]).append("\n");
            b.append("}\n\n");
        }
        if ("raw".equals(r.bodyMode) && r.bodyRaw != null) {
            b.append("body:").append(r.bodyLanguage).append(" {\n").append(r.bodyRaw).append("\n}\n\n");
        }
        return b.toString().stripTrailing() + "\n";
    }

    private String fullUrl(Collection.Request r) {
        StringBuilder u = new StringBuilder(r.urlTemplate);
        for (int i = 0; i < r.query.size(); i++) {
            u.append(i == 0 && r.urlTemplate.indexOf('?') < 0 ? "?" : "&");
            u.append(r.query.get(i)[0]).append("=").append(r.query.get(i)[1]);
        }
        return u.toString();
    }

    private String envBru(EnvironmentModel env) {
        StringBuilder b = new StringBuilder("vars {\n");
        for (Map.Entry<String, String> e : env.values.entrySet())
            b.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        b.append("}\n");
        return b.toString();
    }

    private static String folderSlug(String s) { return slug(s); }
    private static String requestSlug(String s) { return slug(s); }
    private static String slug(String s) { return s == null ? "x" : s.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", ""); }
}
