package apicollector.core;

import apicollector.model.Endpoint;
import apicollector.model.EndpointShape;
import apicollector.model.Observation;

import java.util.*;

/**
 * Central, thread-safe state tying grouping + coverage + known-values together.
 * Montoya-free: fed Observation DTOs. This is the object the UI tabs read from.
 */
public final class CaptureStore {
    private final PathNormalizer norm;
    private final Map<EndpointShape, Endpoint> endpoints = new LinkedHashMap<>();
    private final ParameterRegistry params = new ParameterRegistry();
    private final CoverageTracker coverage = new CoverageTracker();
    private volatile long version;
    private volatile LoadedCollection collection;

    public CaptureStore() { this(new PathNormalizer()); }
    public CaptureStore(PathNormalizer norm) { this.norm = norm; }

    public synchronized Endpoint record(Observation o) {
        EndpointShape shape = norm.shapeObserved(o.method, o.path);
        Endpoint ep = endpoints.computeIfAbsent(shape, Endpoint::new);
        if (ep.representative == null) ep.representative = o;
        ep.count++;
        coverage.markSeen(shape);

        String src = "req:" + shape.canonical();
        // path params: walk concrete segments against the shape, derive a name per param slot
        List<String> concrete = split(o.path);
        List<String> shaped = shape.segments();
        for (int i = 0; i < shaped.size() && i < concrete.size(); i++) {
            if (EndpointShape.PARAM.equals(shaped.get(i))) {
                params.record(PathParamNamer.nameFor(shaped, i), concrete.get(i), "path:" + shape.canonical());
            }
        }
        o.queryParams.forEach((k, v) -> params.record(k, v, src));
        o.bodyParams.forEach((k, v)  -> params.record(k, v, src));
        // response fields are the important ones (order_id needed as input elsewhere)
        o.responseParams.forEach((k, v) -> params.record(k, v, "resp:" + shape.canonical()));
        version++;
        return ep;
    }

    public synchronized void importExpected(String method, String path) {
        coverage.addExpected(norm.shapeExpected(method, path));
        version++;
    }

    /** The client-provided collection being hydrated, if one is loaded. */
    public LoadedCollection collection() { return collection; }

    public synchronized void loadCollection(LoadedCollection lc) {
        this.collection = lc;
        params.clearWanted();
        if (lc != null) for (String n : lc.placeholderNames()) params.markWanted(n);
        version++;
    }
    public synchronized void unloadCollection() { this.collection = null; params.clearWanted(); version++; }

    /** Bumped on every mutation so the UI can refresh only when something actually changed. */
    public long version() { return version; }

    /** Forget captured traffic (endpoints + known values + seen marks); keeps the imported list. */
    public synchronized void clearCaptured() {
        endpoints.clear(); params.clear(); coverage.clearSeen(); version++;
    }
    /** Forget the imported expected-endpoint list; keeps captured traffic. */
    public synchronized void clearExpected() { coverage.clearExpected(); version++; }

    public synchronized void clearAll() {
        endpoints.clear(); params.clear(); coverage.clearSeen(); coverage.clearExpected();
        collection = null; params.clearWanted();   // a full reset drops the loaded client collection too
        version++;
    }

    public synchronized Collection<Endpoint> endpoints() { return new ArrayList<>(endpoints.values()); }
    public ParameterRegistry params()  { return params; }
    public CoverageTracker coverage()  { return coverage; }

    private static List<String> split(String p) {
        List<String> out = new ArrayList<>();
        if (p == null) return out;
        int q = p.indexOf('?'); if (q >= 0) p = p.substring(0, q);
        for (String s : p.split("/")) if (!s.isEmpty()) out.add(s);
        return out;
    }
}
