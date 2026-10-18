package apicollector.ingest;

import apicollector.export.Json;
import java.util.*;

/**
 * Detects the format of an imported artifact and routes to the right interpreter.
 * JSON / CSV / freeform are fully handled here. YAML and XLSX need a decode step
 * (SnakeYAML/Jackson, Apache POI) that produces a Map or rows for the shared interpreters.
 */
public final class ImportDispatcher {
    private final OpenApiImporter openApi = new OpenApiImporter();
    private final PostmanImporter postman = new PostmanImporter();
    private final FreeformImporter freeform = new FreeformImporter();
    private final TabularImporter tabular = new TabularImporter();
    private final YamlSpecDecoder yaml = new YamlSpecDecoder();
    private final XlsxSpecDecoder xlsx = new XlsxSpecDecoder();
    private final BrunoImporter bruno = new BrunoImporter();

    /** Binary-aware entry point. Use for files (handles .xlsx); delegates everything else to importText. */
    public List<ImportedEndpoint> importFile(java.nio.file.Path path) throws Exception {
        if (java.nio.file.Files.isDirectory(path)) return importDirectory(path);
        String name = path.getFileName().toString();
        String ext = ext(name);
        if (ext.equals("xlsx") || ext.equals("xls")) {
            try (var in = java.nio.file.Files.newInputStream(path)) { return xlsx.decode(in, name); }
            catch (NoClassDefFoundError missingPoi) {
                throw new UnsupportedOperationException(
                    "Excel support needs the packaged build. Run ./gradlew shadowJar (bundles Apache POI), "
                    + "or export the sheet to CSV and import that.");
            }
        }
        return importText(name, java.nio.file.Files.readString(path));
    }

    /** Import every .bru file under a Bruno collection folder (recursively). */
    public List<ImportedEndpoint> importDirectory(java.nio.file.Path dir) throws Exception {
        List<ImportedEndpoint> out = new ArrayList<>();
        try (var walk = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path f : walk.filter(java.nio.file.Files::isRegularFile)
                                            .filter(f -> f.toString().toLowerCase().endsWith(".bru"))
                                            .toList()) {
                out.addAll(bruno.parse(java.nio.file.Files.readString(f), f.getFileName().toString()));
            }
        }
        return out;
    }

    public List<ImportedEndpoint> importText(String filename, String content) {
        String ext = ext(filename);
        String trimmed = content == null ? "" : content.stripLeading();

        if (ext.equals("json") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                Object parsed = Json.read(content);
                if (parsed instanceof Map<?, ?> m0) {
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) m0;
                    if (m.containsKey("openapi") || m.containsKey("swagger") || m.containsKey("paths"))
                        return openApi.fromMap(m, filename);
                    if (m.containsKey("item") || isPostmanInfo(m.get("info")) || isWrappedPostman(m.get("collection")))
                        return postman.fromMap(m, filename);
                }
            } catch (RuntimeException ignore) { /* fall through to freeform */ }
        }
        if (ext.equals("bru")) return bruno.parse(content, filename);
        if (ext.equals("csv")) return tabular.fromRows(TabularImporter.parseCsv(content), filename);
        if (ext.equals("yaml") || ext.equals("yml") || startsWithSpecMarker(trimmed))
            return yaml.decode(content, filename);
        if (ext.equals("xlsx") || ext.equals("xls"))
            throw new UnsupportedOperationException("Excel is binary \u2014 call importFile(path) instead of importText");
        return freeform.parse(content, filename);
    }

    @SuppressWarnings("unchecked")
    private static boolean isWrappedPostman(Object collection) {
        return collection instanceof Map
            && (((Map<String, Object>) collection).containsKey("item")
                || isPostmanInfo(((Map<String, Object>) collection).get("info")));
    }

    private static boolean isPostmanInfo(Object info) {
        if (!(info instanceof Map)) return false;
        Object schema = ((Map<?, ?>) info).get("schema");
        return schema instanceof String s && s.contains("getpostman.com");
    }
    private static boolean startsWithSpecMarker(String t) {
        String head = t.toLowerCase();
        return head.startsWith("openapi:") || head.startsWith("swagger:");
    }
    private static String ext(String name) {
        if (name == null) return "";
        int d = name.lastIndexOf('.');
        return d >= 0 ? name.substring(d + 1).toLowerCase() : "";
    }
}
