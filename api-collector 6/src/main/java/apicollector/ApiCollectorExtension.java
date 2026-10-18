package apicollector;

import apicollector.burp.*;
import apicollector.core.CaptureStore;
import apicollector.ui.ApiCollectorTab;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/** Entry point. Registered via META-INF/services/burp.api.montoya.BurpExtension. */
public final class ApiCollectorExtension implements BurpExtension {
    @Override public void initialize(MontoyaApi api) {
        api.extension().setName("API Collector");

        CaptureStore store = new CaptureStore();
        ScopeController scope = new ScopeController(api);
        ObservationFactory factory = new ObservationFactory();

        // Live capture: every in-scope response is recorded automatically, no clicking.
        LiveHttpHandler handler = new LiveHttpHandler(store, scope, factory, api);
        api.http().registerHttpHandler(handler);

        ApiCollectorTab tab = new ApiCollectorTab(api, store, scope, factory, handler, ApiCollectorTab.Layout.VERTICAL_SPLIT);
        api.userInterface().registerSuiteTab("API Collector", tab.component());

        // Also backfill anything already in proxy history, off the UI thread so load isn't blocked.
        Thread backfill = new Thread(() -> {
            try {
                int n = new ProxyHistoryImporter().importInto(store, api, scope, factory);
                api.logging().logToOutput("API Collector: backfilled " + n + " in-scope items from proxy history.");
            } catch (Exception e) {
                api.logging().logToError("API Collector: proxy history backfill failed", e);
            }
        }, "api-collector-backfill");
        backfill.setDaemon(true);
        backfill.start();

        api.logging().logToOutput("API Collector loaded \u2014 capturing automatically. Walk the app, then Export.");
    }
}
