package apicollector.burp;

import apicollector.core.JsonFlattener;
import apicollector.model.Observation;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/** Builds a Montoya-free {@link Observation} from Burp request/response objects. */
public final class ObservationFactory {
    private final JsonFlattener flattener = new JsonFlattener();

    public Observation from(HttpRequest req, HttpResponse resp) {
        Observation o = new Observation();
        o.method = req.method();
        o.path = req.pathWithoutQuery();
        HttpService svc = req.httpService();
        o.host = (svc.secure() ? "https://" : "http://") + svc.host();

        for (ParsedHttpParameter p : req.parameters()) {
            if (p.type() == HttpParameterType.URL) o.queryParams.put(p.name(), p.value());
            else if (p.type() == HttpParameterType.BODY) o.bodyParams.put(p.name(), p.value());
        }
        o.bodyParams.putAll(flattener.flatten(req.bodyToString())); // JSON bodies

        for (HttpHeader h : req.headers()) {
            if (h.name().equalsIgnoreCase("authorization")) o.headers.put("Authorization", h.value());
            else if (h.name().equalsIgnoreCase("content-type")) o.headers.put("Content-Type", h.value());
        }
        if (resp != null) o.responseParams.putAll(flattener.flatten(resp.bodyToString()));
        return o;
    }
}
