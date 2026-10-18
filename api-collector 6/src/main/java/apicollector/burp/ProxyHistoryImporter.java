package apicollector.burp;

import apicollector.core.CaptureStore;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

/** Seed capture from Burp's existing Proxy HTTP history (in-scope items only). */
public final class ProxyHistoryImporter {
    public int importInto(CaptureStore store, MontoyaApi api, ScopeController scope, ObservationFactory factory) {
        int n = 0;
        for (ProxyHttpRequestResponse it : api.proxy().history()) {
            var req = it.finalRequest();
            if (req != null && scope.inScope(req.url())) { store.record(factory.from(req, it.response())); n++; }
        }
        return n;
    }
}
