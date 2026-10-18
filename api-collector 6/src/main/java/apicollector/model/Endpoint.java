package apicollector.model;

/** A logical endpoint (one normalized shape) plus a representative sample and a hit count. */
public final class Endpoint {
    public final EndpointShape shape;
    public Observation representative;
    public int count;
    public Endpoint(EndpointShape shape) { this.shape = shape; }
}
