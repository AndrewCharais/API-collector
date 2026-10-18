package apicollector.burp;

import burp.api.montoya.MontoyaApi;
import java.util.*;

/** Burp target scope, or a custom host list (one host per line; subdomains match). */
public final class ScopeController {
    public enum Mode { BURP, CUSTOM }

    private final MontoyaApi api;
    private volatile Mode mode = Mode.BURP;
    private final Set<String> includeHosts = Collections.synchronizedSet(new LinkedHashSet<>());

    public ScopeController(MontoyaApi api) { this.api = api; }

    public Mode mode() { return mode; }
    public void setMode(Mode m) { this.mode = m; }

    public Set<String> hosts() { synchronized (includeHosts) { return new LinkedHashSet<>(includeHosts); } }

    /** Replace the whole list from a textarea: one host per line, blanks/comments ignored. */
    public void setHosts(String text) {
        synchronized (includeHosts) {
            includeHosts.clear();
            if (text == null) return;
            for (String raw : text.split("\\R")) {
                String h = normalizeHost(raw);
                if (!h.isEmpty()) includeHosts.add(h);
            }
        }
    }

    public boolean inScope(String url) {
        if (mode == Mode.BURP) return api != null && api.scope().isInScope(url);
        String host = hostOf(url);
        if (host.isEmpty()) return false;
        synchronized (includeHosts) {
            for (String h : includeHosts) if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    /** Strip scheme, userinfo, port, path. Handles localhost, IPv4, and bracketed IPv6. */
    static String hostOf(String url) {
        if (url == null) return "";
        String s = url.trim();
        int scheme = s.indexOf("://"); if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');    if (slash >= 0)  s = s.substring(0, slash);
        int at = s.lastIndexOf('@');   if (at >= 0)     s = s.substring(at + 1);
        if (s.startsWith("[")) { int c = s.indexOf(']'); return c > 0 ? s.substring(1, c).toLowerCase() : ""; }
        int colon = s.indexOf(':');    if (colon >= 0)  s = s.substring(0, colon);
        return s.toLowerCase();
    }
    private static String normalizeHost(String line) {
        String s = line.trim();
        if (s.isEmpty() || s.startsWith("#")) return "";
        return hostOf(s.contains("://") ? s : "//" + s); // tolerate pasted full URLs too
    }
}
