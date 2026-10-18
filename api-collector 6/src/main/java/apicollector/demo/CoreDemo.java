package apicollector.demo;

import apicollector.core.PathNormalizer;
import apicollector.export.Collection;
import apicollector.export.CollectionExporter;
import apicollector.export.EnvironmentModel;
import apicollector.export.PlaceholderStyle;
import apicollector.export.PostmanExporter;
import apicollector.export.BrunoExporter;
import apicollector.model.EndpointShape;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercises the verified core: grouping, coverage matching, and both exporters. */
public final class CoreDemo {
    public static void main(String[] args) {
        PathNormalizer norm = new PathNormalizer();

        System.out.println("=== 1. Endpoint grouping (observed traffic) ===");
        String[][] observed = {
            {"GET",  "/api/users/123"},
            {"GET",  "/api/users/456"},
            {"GET",  "/api/users/507f1f77bcf86cd799439011/orders"},           // mongo id
            {"POST", "/api/users/9b2e4c1a-1f77-4a3b-8c2d-1e2f3a4b5c6d/orders"},// uuid
            {"GET",  "/api/v1/search"},                                        // v1 stays literal
            {"GET",  "/api/users/me"},                                         // me stays literal
        };
        Map<EndpointShape, Integer> groups = new LinkedHashMap<>();
        for (String[] o : observed) {
            EndpointShape s = norm.shapeObserved(o[0], o[1]);
            groups.merge(s, 1, Integer::sum);
            System.out.printf("  %-6s %-60s -> %s%n", o[0], o[1], s.method() + " " + s.displayTemplate());
        }
        System.out.println("  distinct endpoints: " + groups.size());

        System.out.println("\n=== 2. Coverage (spec {id} / :id / concrete all match traffic) ===");
        Set<EndpointShape> seen = groups.keySet();
        String[][] expected = {
            {"GET",  "/api/users/{id}"},          // curly     -> matches /users/123
            {"POST", "/api/users/:id/orders"},    // colon     -> matches uuid traffic
            {"GET",  "/api/users/42/orders"},     // concrete  -> matches mongo traffic
            {"DELETE","/api/users/{id}"},         // never triggered from UI
        };
        for (String[] e : expected) {
            EndpointShape s = norm.shapeExpected(e[0], e[1]);
            boolean hit = seen.contains(s);
            System.out.printf("  [%s] %-6s %-28s (shape %s)%n", hit ? "SEEN    " : "NOT SEEN", e[0], e[1], s);
        }

        System.out.println("\n=== 3. Export with Known-Value placeholders ===");
        PlaceholderStyle ph = PlaceholderStyle.DOUBLE_CURLY;
        Collection col = new Collection("Client API");
        Collection.Request r = new Collection.Request();
        r.name = "GET /users/{id}";
        r.method = "GET";
        r.urlTemplate = ph.wrap("baseUrl") + "/api/users/" + ph.wrap("userId"); // path param templated
        r.header("Authorization", "Bearer " + ph.wrap("accessToken"));
        r.query("expand", ph.wrap("expand"));
        col.folder("users").requests.add(r);

        Collection.Request post = new Collection.Request();
        post.name = "POST /users/{id}/orders";
        post.method = "POST";
        post.urlTemplate = ph.wrap("baseUrl") + "/api/users/" + ph.wrap("userId") + "/orders";
        post.header("Content-Type", "application/json");
        post.bodyMode = "raw";
        post.bodyLanguage = "json";
        post.bodyRaw = "{\n  \"orderId\": \"" + ph.wrap("orderId") + "\",\n  \"qty\": 2\n}";
        col.folder("users").requests.add(post);

        EnvironmentModel env = new EnvironmentModel("Local")
            .put("baseUrl", "https://api.client.example.com")
            .put("userId", "123")
            .put("accessToken", "eyJ...redacted")
            .put("expand", "profile")
            .put("orderId", "507f1f77bcf86cd799439011"); // sourced from a response body

        for (CollectionExporter exp : List.of(new PostmanExporter(), new BrunoExporter())) {
            System.out.println("\n----- exporter: " + exp.id() + " -----");
            Map<String, String> files = exp.export(col, env, "Client_API");
            for (String name : files.keySet()) System.out.println("  file: " + name);
        }

        // dump the two most illustrative files for validation
        System.out.println("\n### POSTMAN COLLECTION ###");
        System.out.println(new PostmanExporter().export(col, env, "Client_API").get("Client_API.postman_collection.json"));
        System.out.println("\n### BRUNO REQUEST (post) ###");
        System.out.println(new BrunoExporter().export(col, env, "Client_API").get("Client_API/users/POST__users_{id}_orders.bru"));
    }
}
