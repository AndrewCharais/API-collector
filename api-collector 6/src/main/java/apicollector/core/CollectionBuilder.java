package apicollector.core;

import apicollector.export.Collection;
import apicollector.export.EnvironmentModel;
import apicollector.export.PlaceholderStyle;
import apicollector.model.Endpoint;
import apicollector.model.EndpointShape;
import apicollector.model.Observation;

import java.util.List;
import java.util.Map;

/**
 * Turns captured endpoints into the export IR. Any field whose NAME is a Known Value is
 * replaced with a placeholder; the companion environment is the registry's active snapshot.
 * Pure — no Burp, no exporter specifics.
 */
public final class CollectionBuilder {
    private final PlaceholderStyle ph;
    private final String baseUrlVar;

    public CollectionBuilder(PlaceholderStyle ph, String baseUrlVar) {
        this.ph = ph; this.baseUrlVar = baseUrlVar;
    }

    public Collection build(String name, CaptureStore store) {
        Collection c = new Collection(name);
        ParameterRegistry reg = store.params();
        for (Endpoint ep : store.endpoints()) {
            Observation o = ep.representative;
            if (o == null) continue;
            Collection.Request r = new Collection.Request();
            r.method = ep.shape.method();
            r.name = ep.shape.method() + " " + ep.shape.displayTemplate();
            r.urlTemplate = ph.wrap(baseUrlVar) + pathTemplate(ep.shape);

            for (Map.Entry<String, String> q : o.queryParams.entrySet())
                r.query(q.getKey(), reg.knows(q.getKey()) ? ph.wrap(q.getKey()) : q.getValue());

            String auth = o.headers.get("Authorization");
            if (auth != null) r.header("Authorization", templatizeAuth(auth));
            String ct = o.headers.get("Content-Type");
            if (ct != null) r.header("Content-Type", ct);

            if (!o.bodyParams.isEmpty()) {
                r.bodyMode = "raw"; r.bodyLanguage = "json";
                r.bodyRaw = jsonBody(o.bodyParams, reg);
            }
            c.folder(folderOf(ep.shape)).requests.add(r);
        }
        return c;
    }

    /** environment file = currently-active value for every known parameter, + baseUrl. */
    public EnvironmentModel environment(String envName, String baseUrl, CaptureStore store) {
        EnvironmentModel env = new EnvironmentModel(envName);
        env.put(baseUrlVar, baseUrl);
        store.params().activeSnapshot().forEach(env::put);
        return env;
    }

    private String pathTemplate(EndpointShape shape) {
        List<String> segs = shape.segments();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.size(); i++) {
            sb.append('/');
            sb.append(EndpointShape.PARAM.equals(segs.get(i))
                ? ph.wrap(PathParamNamer.nameFor(segs, i)) : segs.get(i));
        }
        return sb.toString();
    }
    private String templatizeAuth(String value) {
        int sp = value.indexOf(' ');
        String scheme = sp > 0 ? value.substring(0, sp + 1) : "";
        return scheme + ph.wrap("accessToken");
    }
    private String jsonBody(Map<String, String> body, ParameterRegistry reg) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : body.entrySet()) {
            if (i++ > 0) sb.append(",\n");
            String v = reg.knows(e.getKey()) ? ph.wrap(e.getKey()) : e.getValue();
            sb.append("  \"").append(e.getKey()).append("\": \"").append(v).append("\"");
        }
        return sb.append("\n}").toString();
    }
    private static String folderOf(EndpointShape shape) {
        for (String s : shape.segments()) if (!EndpointShape.PARAM.equals(s) && !s.equals("api")) return s;
        return "root";
    }
}
