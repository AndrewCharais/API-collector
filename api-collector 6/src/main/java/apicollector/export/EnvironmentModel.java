package apicollector.export;

import java.util.LinkedHashMap;
import java.util.Map;

/** name -> currently-selected value, emitted as the companion environment file. */
public final class EnvironmentModel {
    public String name;
    public final Map<String, String> values = new LinkedHashMap<>();
    public EnvironmentModel(String name) { this.name = name; }
    public EnvironmentModel put(String k, String v) { values.put(k, v); return this; }
}
