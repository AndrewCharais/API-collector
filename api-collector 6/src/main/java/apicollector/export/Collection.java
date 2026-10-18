package apicollector.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Exporter-agnostic intermediate representation. Built once from captured traffic;
 * every exporter reads ONLY this (never Burp/Montoya types). Field values are already
 * templated with placeholders where a Known Value applied.
 */
public final class Collection {
    public String name;
    public final List<Folder> folders = new ArrayList<>();
    public Collection(String name) { this.name = name; }

    public Folder folder(String name) {
        for (Folder f : folders) if (f.name.equals(name)) return f;
        Folder f = new Folder(name); folders.add(f); return f;
    }

    public static final class Folder {
        public final String name;
        public final List<Request> requests = new ArrayList<>();
        public Folder(String name) { this.name = name; }
    }

    public static final class Request {
        public String name;
        public String method;
        public String urlTemplate;                 // e.g. {{baseUrl}}/users/{{userId}}
        public final List<String[]> headers = new ArrayList<>(); // {name, value}
        public final List<String[]> query   = new ArrayList<>(); // {name, valueTemplate}
        public String bodyMode = "none";           // "none" | "raw"
        public String bodyRaw;                     // templated
        public String bodyLanguage = "json";       // json | text | ...
        public void header(String n, String v) { headers.add(new String[]{n, v}); }
        public void query(String n, String v)  { query.add(new String[]{n, v}); }
    }
}
