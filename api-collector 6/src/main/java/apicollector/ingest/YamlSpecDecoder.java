package apicollector.ingest;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodes an OpenAPI/Swagger YAML document into a Map, then reuses the already-tested
 * {@link OpenApiImporter#fromMap}. SnakeYAML produces the same Map shape as JSON parsing,
 * so the interpreter is shared — this class only does the decode step.
 */
public final class YamlSpecDecoder {
    private final OpenApiImporter openApi = new OpenApiImporter();

    @SuppressWarnings("unchecked")
    public java.util.List<ImportedEndpoint> decode(String yamlText, String source) {
        LoaderOptions opts = new LoaderOptions();
        opts.setMaxAliasesForCollections(200);
        opts.setCodePointLimit(20 * 1024 * 1024); // 20 MB, for large specs
        Object doc = new Yaml(opts).load(yamlText);
        Map<String, Object> map = doc instanceof Map ? (Map<String, Object>) doc : new LinkedHashMap<>();
        return openApi.fromMap(map, source);
    }
}
