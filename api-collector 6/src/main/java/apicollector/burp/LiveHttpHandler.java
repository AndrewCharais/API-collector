package apicollector.burp;

import apicollector.core.CaptureStore;
import apicollector.core.LiveSubstitution;
import apicollector.export.PlaceholderStyle;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.*;

/**
 * Live capture, plus optional on-the-wire placeholder substitution for API-only testing.
 * Auto-fill has its own settings (formats, tools) because the format you MATCH in a client
 * collection is not necessarily the format you EXPORT.
 */
public final class LiveHttpHandler implements HttpHandler {
    private static final Set<String> STATIC_EXT = Set.of(
        "js","css","png","jpg","jpeg","gif","svg","ico","woff","woff2","ttf","eot","map","webp");

    private final CaptureStore store;
    private final ScopeController scope;
    private final ObservationFactory factory;
    private final MontoyaApi api;
    private final LiveSubstitution substitution = new LiveSubstitution();

    private volatile boolean enabled = true;               // capture
    private volatile boolean substituteLive = false;       // rewrite outgoing requests
    private volatile boolean skipStatic = true;            // ignore js/css/images when capturing
    private volatile List<PlaceholderStyle> styles = List.of(PlaceholderStyle.DOUBLE_CURLY);
    private volatile Set<ToolType> tools =
        new LinkedHashSet<>(List.of(ToolType.PROXY, ToolType.REPEATER, ToolType.INTRUDER));
    private volatile String baseUrlFallback = "";

    public LiveHttpHandler(CaptureStore store, ScopeController scope, ObservationFactory factory, MontoyaApi api) {
        this.store = store; this.scope = scope; this.factory = factory; this.api = api;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isSubstituteLive() { return substituteLive; }
    public void setSubstituteLive(boolean s) { this.substituteLive = s; }
    public void setSkipStatic(boolean s) { this.skipStatic = s; }
    public boolean isSkipStatic() { return skipStatic; }
    public void setPlaceholderStyles(List<PlaceholderStyle> s) {
        if (s != null && !s.isEmpty()) this.styles = List.copyOf(s);
    }
    public List<PlaceholderStyle> placeholderStyles() { return styles; }
    public void setTools(Set<ToolType> t) { if (t != null) this.tools = new LinkedHashSet<>(t); }
    public Set<ToolType> tools() { return tools; }
    public void setBaseUrlFallback(String s) { this.baseUrlFallback = s == null ? "" : s; }

    @Override public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent req) {
        if (!substituteLive) return RequestToBeSentAction.continueWith(req);
        try {
            if (!toolAllowed(req)) return RequestToBeSentAction.continueWith(req);
            if (!scope.inScope(req.url())) return RequestToBeSentAction.continueWith(req);

            Map<String, String> values = new LinkedHashMap<>(store.params().activeSnapshot());
            if (!baseUrlFallback.isBlank()) values.putIfAbsent("baseUrl", baseUrlFallback);

            String raw = req.toString();
            Set<String> filled = new LinkedHashSet<>(), missing = new LinkedHashSet<>();
            boolean changed = false;
            for (PlaceholderStyle style : styles) {          // try every configured format
                LiveSubstitution.Outcome out = substitution.apply(raw, style, values);
                missing.addAll(out.missing);
                if (out.changed) { raw = out.raw; changed = true; filled.addAll(out.filled); }
            }
            if (!changed) return RequestToBeSentAction.continueWith(req);

            missing.removeAll(filled);
            api.logging().logToOutput("API Collector: filled " + filled + " in "
                + req.method() + " " + req.pathWithoutQuery()
                + (missing.isEmpty() ? "" : "  (no value yet for " + missing + ")"));
            return RequestToBeSentAction.continueWith(HttpRequest.httpRequest(req.httpService(), raw));
        } catch (Exception e) {
            api.logging().logToError("API Collector substitution error", e);
            return RequestToBeSentAction.continueWith(req);
        }
    }

    @Override public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived resp) {
        try {
            if (!enabled) return ResponseReceivedAction.continueWith(resp);
            var req = resp.initiatingRequest();
            if (req != null && scope.inScope(req.url()) && !isStatic(req))
                store.record(factory.from(req, resp));
        } catch (Exception e) {
            api.logging().logToError("API Collector capture error", e);
        }
        return ResponseReceivedAction.continueWith(resp);
    }

    private boolean toolAllowed(HttpRequestToBeSent req) {
        try {
            var src = req.toolSource();
            return src == null || tools.contains(src.toolType());
        } catch (Exception e) { return true; }
    }
    private boolean isStatic(HttpRequest req) {
        if (!skipStatic) return false;
        String ext = req.fileExtension();
        return ext != null && STATIC_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }
}
