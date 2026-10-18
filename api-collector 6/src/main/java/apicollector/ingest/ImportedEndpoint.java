package apicollector.ingest;

/** One expected endpoint parsed from an imported artifact, before shape-normalization. */
public final class ImportedEndpoint {
    public final String method;   // GET, POST, ...
    public final String path;     // /api/users/{id}  (may still contain {id}, :id, {{id}})
    public final String source;   // where it came from, for provenance/UI
    public ImportedEndpoint(String method, String path, String source) {
        this.method = method == null ? "GET" : method.toUpperCase();
        this.path = path;
        this.source = source;
    }
    @Override public String toString() { return method + " " + path; }
}
