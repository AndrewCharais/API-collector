package apicollector.core;

import apicollector.model.EndpointShape;
import java.util.List;

/** Derives a stable name for a path-param slot: previous literal, de-pluralized, + "Id". */
public final class PathParamNamer {
    private PathParamNamer() {}
    public static String nameFor(List<String> shapedSegments, int idx) {
        String prev = null;
        for (int j = idx - 1; j >= 0; j--)
            if (!EndpointShape.PARAM.equals(shapedSegments.get(j))) { prev = shapedSegments.get(j); break; }
        if (prev == null) return "id";
        String base = prev.endsWith("ies") ? prev.substring(0, prev.length() - 3) + "y"
                    : prev.endsWith("s")   ? prev.substring(0, prev.length() - 1)
                    : prev;
        return base + "Id";
    }
}
