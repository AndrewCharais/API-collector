package apicollector.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Postman Collection v2.1 + companion Environment JSON. Reads only the IR. */
public final class PostmanExporter implements CollectionExporter {
    private static final String SCHEMA =
        "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    @Override public String id() { return "postman"; }

    @Override public Map<String, String> export(Collection c, EnvironmentModel env, String baseName) {
        String base = sanitize(baseName == null || baseName.isBlank() ? c.name : baseName);
        Map<String, String> out = new LinkedHashMap<>();
        out.put(base + ".postman_collection.json", Json.write(collection(c)));
        if (env != null) out.put(base + ".postman_environment.json", Json.write(environment(env)));
        return out;
    }

    private Map<String, Object> collection(Collection c) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", c.name);
        info.put("_postman_id", UUID.randomUUID().toString());
        info.put("schema", SCHEMA);
        root.put("info", info);

        List<Object> items = new ArrayList<>();
        for (Collection.Folder f : c.folders) {
            Map<String, Object> folder = new LinkedHashMap<>();
            folder.put("name", f.name);
            List<Object> reqs = new ArrayList<>();
            for (Collection.Request r : f.requests) reqs.add(requestItem(r));
            folder.put("item", reqs);
            items.add(folder);
        }
        root.put("item", items);
        return root;
    }

    private Map<String, Object> requestItem(Collection.Request r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", r.name);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("method", r.method);

        List<Object> headers = new ArrayList<>();
        for (String[] h : r.headers) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("key", h[0]); hm.put("value", h[1]);
            headers.add(hm);
        }
        req.put("header", headers);

        req.put("url", url(r));

        if ("raw".equals(r.bodyMode) && r.bodyRaw != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mode", "raw");
            body.put("raw", r.bodyRaw);
            Map<String, Object> opts = new LinkedHashMap<>();
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("language", r.bodyLanguage);
            opts.put("raw", raw);
            body.put("options", opts);
            req.put("body", body);
        }
        item.put("request", req);
        return item;
    }

    /** Split {{baseUrl}}/a/b?x=1 into Postman's url object (raw + host + path + query). */
    private Map<String, Object> url(Collection.Request r) {
        Map<String, Object> url = new LinkedHashMap<>();
        StringBuilder raw = new StringBuilder(r.urlTemplate);
        if (!r.query.isEmpty()) {
            raw.append(raw.indexOf("?") >= 0 ? "&" : "?");
            for (int i = 0; i < r.query.size(); i++) {
                if (i > 0) raw.append("&");
                raw.append(r.query.get(i)[0]).append("=").append(r.query.get(i)[1]);
            }
        }
        url.put("raw", raw.toString());

        String noQuery = r.urlTemplate;
        int q = noQuery.indexOf('?'); if (q >= 0) noQuery = noQuery.substring(0, q);
        String host = noQuery, path = "";
        int slash = indexOfPathStart(noQuery);
        if (slash >= 0) { host = noQuery.substring(0, slash); path = noQuery.substring(slash + 1); }
        url.put("host", List.of(host));
        List<Object> pathParts = new ArrayList<>();
        for (String p : path.split("/")) if (!p.isEmpty()) pathParts.add(p);
        url.put("path", pathParts);

        if (!r.query.isEmpty()) {
            List<Object> ql = new ArrayList<>();
            for (String[] kv : r.query) {
                Map<String, Object> qm = new LinkedHashMap<>();
                qm.put("key", kv[0]); qm.put("value", kv[1]);
                ql.add(qm);
            }
            url.put("query", ql);
        }
        return url;
    }

    /** First '/' that starts the path, skipping a leading {{baseUrl}} token. */
    private int indexOfPathStart(String s) {
        if (s.startsWith("{{")) { int close = s.indexOf("}}"); if (close >= 0) return s.indexOf('/', close); }
        int scheme = s.indexOf("://");
        return s.indexOf('/', scheme >= 0 ? scheme + 3 : 0);
    }

    private Map<String, Object> environment(EnvironmentModel env) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", UUID.randomUUID().toString());
        root.put("name", env.name);
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, String> e : env.values.entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("key", e.getKey());
            v.put("value", e.getValue());
            v.put("enabled", true);
            v.put("type", "default");
            values.add(v);
        }
        root.put("values", values);
        root.put("_postman_variable_scope", "environment");
        return root;
    }

    private static String sanitize(String s) { return s == null ? "collection" : s.replaceAll("[^A-Za-z0-9._-]+", "_"); }
}
