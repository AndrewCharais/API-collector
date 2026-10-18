package apicollector.export;

import java.util.Map;

/** One format = one exporter. Returns filename -> UTF-8 text content (no filesystem coupling). */
public interface CollectionExporter {
    String id();
    /** @param baseName filename stem for the deliverable (e.g. "ACME_Q3_Client-API"). */
    Map<String, String> export(Collection c, EnvironmentModel env, String baseName);
}
