package apicollector.core;

import java.util.*;

/**
 * name -> distinct observed values (with provenance) + a user-selectable "active" value.
 * Backs the Known Values tab and the exported environment file.
 */
public final class ParameterRegistry {
    public static final class Value {
        public final String value;
        public final Set<String> sources = new LinkedHashSet<>(); // e.g. "resp:GET /users/{id}"
        public Value(String value) { this.value = value; }
    }

    private final Map<String, LinkedHashMap<String, Value>> byName = new LinkedHashMap<>();
    private final Map<String, String> active = new LinkedHashMap<>();
    /** Where each active value came from — auto-refresh only accepts a newer value from the SAME call. */
    private final Map<String, String> activeSource = new LinkedHashMap<>();
    /** Placeholder names a loaded collection needs — shown even before a value is captured. */
    private final Set<String> wanted = new LinkedHashSet<>();
    /** Names the user chose explicitly — never auto-overwritten. */
    private final Set<String> pinned = new LinkedHashSet<>();
    /** When true, a newly captured value replaces the active one (e.g. refreshed auth tokens). */
    private volatile boolean autoRefresh = true;

    public synchronized void record(String name, String value, String source) {
        if (name == null || value == null || value.isEmpty()) return;
        var vals = byName.computeIfAbsent(name, k -> new LinkedHashMap<>());
        vals.computeIfAbsent(value, Value::new).sources.add(source);
        if (!active.containsKey(name)) {                       // first sighting wins the slot
            active.put(name, value);
            activeSource.put(name, source);
        } else if (autoRefresh && !pinned.contains(name)
                   && source.equals(activeSource.get(name))) {  // same call returned a new value
            active.put(name, value);                            // e.g. re-login -> fresh token
        }
        // a same-named field from a DIFFERENT call is recorded but never steals the active slot
    }

    /** Register a placeholder the loaded collection needs. Appears in the table as "waiting". */
    public synchronized void markWanted(String name) { if (name != null && !name.isBlank()) wanted.add(name); }
    public synchronized void clearWanted() { wanted.clear(); }
    public synchronized boolean isWanted(String name) { return wanted.contains(name); }
    public synchronized Set<String> wanted() { return new LinkedHashSet<>(wanted); }
    /** Wanted names that still have no captured value. */
    public synchronized List<String> missing() {
        List<String> out = new ArrayList<>();
        for (String w : wanted) if (!byName.containsKey(w)) out.add(w);
        return out;
    }

    /** Captured names plus any wanted-but-not-yet-captured names. */
    public synchronized List<String> names() {
        LinkedHashSet<String> all = new LinkedHashSet<>(byName.keySet());
        all.addAll(wanted);
        return new ArrayList<>(all);
    }
    public synchronized List<Value> valuesOf(String name) {
        var v = byName.get(name); return v == null ? List.of() : new ArrayList<>(v.values());
    }
    public synchronized String active(String name) { return active.get(name); }
    public synchronized void setActive(String name, String value) {
        if (byName.containsKey(name) && byName.get(name).containsKey(value)) {
            active.put(name, value);
            var v = byName.get(name).get(value);
            if (v != null && !v.sources.isEmpty()) activeSource.put(name, v.sources.iterator().next());
            pinned.add(name); // explicit choice wins over auto-refresh
        }
    }
    public synchronized boolean isPinned(String name) { return pinned.contains(name); }
    public synchronized void unpin(String name) { pinned.remove(name); }
    public void setAutoRefresh(boolean b) { this.autoRefresh = b; }
    public boolean autoRefresh() { return autoRefresh; }
    public synchronized String activeSource(String name) { return activeSource.get(name); }
    public synchronized Map<String, String> activeSnapshot() { return new LinkedHashMap<>(active); }
    public synchronized boolean knows(String name) { return byName.containsKey(name); }
    public synchronized void clear() { byName.clear(); active.clear(); activeSource.clear(); pinned.clear(); }
    public synchronized void clearAllIncludingWanted() { byName.clear(); active.clear(); wanted.clear(); }
}
