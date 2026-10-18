package apicollector.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Montoya-free input DTO. The live HttpHandler / proxy-history importer build one of
 * these and hand it to CaptureStore, so all core logic stays testable without Burp.
 * Values are pre-flattened (JSON/form -> name->value) by the Montoya-side adapter.
 */
public final class Observation {
    public String method;
    public String host;                 // e.g. api.client.example.com (may include scheme)
    public String path;                 // path only, no query string
    public final Map<String, String> queryParams    = new LinkedHashMap<>();
    public final Map<String, String> bodyParams      = new LinkedHashMap<>();
    public final Map<String, String> responseParams  = new LinkedHashMap<>(); // fields seen in the RESPONSE
    public final Map<String, String> headers         = new LinkedHashMap<>();
}
