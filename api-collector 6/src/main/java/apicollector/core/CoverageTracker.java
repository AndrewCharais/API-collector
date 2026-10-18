package apicollector.core;

import apicollector.model.EndpointShape;
import java.util.*;

/** Expected (from spec) vs seen (from traffic), compared purely on EndpointShape. */
public final class CoverageTracker {
    private final Set<EndpointShape> expected = new LinkedHashSet<>();
    private final Set<EndpointShape> seen     = new LinkedHashSet<>();

    public synchronized void addExpected(EndpointShape s) { expected.add(s); }
    public synchronized void markSeen(EndpointShape s)    { seen.add(s); }

    public synchronized List<EndpointShape> seenExpected() {
        List<EndpointShape> out = new ArrayList<>();
        for (EndpointShape e : expected) if (seen.contains(e)) out.add(e);
        return out;
    }
    public synchronized List<EndpointShape> notSeen() {
        List<EndpointShape> out = new ArrayList<>();
        for (EndpointShape e : expected) if (!seen.contains(e)) out.add(e);
        return out;
    }
    public synchronized int expectedCount() { return expected.size(); }
    public synchronized int seenCount() { return seen.size(); }
    public synchronized void clearExpected() { expected.clear(); }
    public synchronized void clearSeen() { seen.clear(); }
}
