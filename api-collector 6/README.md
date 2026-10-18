# API Collector (Burp / Montoya extension)

Generate a working API collection (Postman / Bruno) + environment file from traffic
captured during normal manual web-app testing, and track it against a client-provided
endpoint list to produce a "couldn't-trigger-from-the-UI" deliverable.

## Load it

A prebuilt `api-collector.jar` is included. In Burp: Extensions > Installed > Add >
Java > select the jar. A new "API Collector" suite tab appears.

Or build from source (needs internet for the Montoya dependency):

    ./gradlew shadowJar   # -> build/libs/api-collector.jar (bundles SnakeYAML + POI)

## Workflow in Burp

1. Import tab — load the in-scope endpoint list (OpenAPI/Swagger, Postman, Excel/CSV,
   or freeform paste). Rows land in Coverage as "not seen".
2. Scope tab — use Burp target scope, or paste client text to auto-extract a host list.
3. Walk the web app through Burp. Live capture records in-scope requests/responses.
   (Or Coverage > "Import Burp proxy history" to seed from existing history.)
4. Coverage tab updates live: seen vs not-seen, matched by normalized endpoint shape.
5. Known values tab — pick the active value per parameter (feeds the environment file).
6. Export tab — write the Postman/Bruno collection + environment; export the not-seen
   list from Coverage as the client deliverable.

## Layout

    apicollector/
      ApiCollectorExtension     BurpExtension entry point (wires everything)
      burp/                     Montoya adapter (the only Burp-coupled code)
        ObservationFactory      HttpRequest/HttpResponse -> Montoya-free Observation
        LiveHttpHandler         live capture on in-scope responses
        ProxyHistoryImporter    seed from Burp proxy history
        ScopeController         Burp scope vs custom host include-list
      core/                     pure logic, unit-tested with no Burp dependency
        PathNormalizer + IdentifierHeuristic + EndpointShape   grouping / coverage shape
        CaptureStore            central state (endpoints + known values + coverage)
        ParameterRegistry, CoverageTracker
        JsonFlattener           JSON body -> field-name -> value
        CollectionBuilder       captured endpoints -> export IR (placeholder templating)
        PathParamNamer
      ingest/                   endpoint-list importers (OpenAPI, Postman, tabular, freeform)
      export/                   exporter-agnostic IR + Postman/Bruno exporters + Json + writer
      ui/ApiCollectorTab        Swing suite tab (Import/Scope/Coverage/Known values/Export)
    src/main/resources/META-INF/services/burp.api.montoya.BurpExtension   ServiceLoader hook

## Design decisions

1. One normalized `EndpointShape` drives BOTH grouping and coverage, so they can never
   disagree about "the same endpoint" — this kills coverage false-negatives.
2. The exporter-agnostic `Collection` IR sits between capture and output; exporters read
   only the IR. Adding Insomnia/OpenAPI = one new `CollectionExporter`.
3. Placeholder templating happens at export time, so one app-walk emits Postman OR Bruno
   with no re-capture.
4. All core logic is fed Montoya-free `Observation` DTOs, so `burp/` stays a thin,
   replaceable adapter and everything else is plain-JUnit testable.

## Import formats

OpenAPI/Swagger (JSON **and** YAML), Postman collections (incl. API-export wrapped),
Excel `.xlsx`, CSV, and freeform paste. YAML uses SnakeYAML; Excel uses Apache POI.
Both are bundled into the jar by the shadow build.

The prebuilt `api-collector.jar` bundles SnakeYAML, so JSON/YAML/Postman/CSV/text work
immediately. Excel needs the packaged build — run `./gradlew shadowJar` (it bundles POI);
until then, `.xlsx` import shows a message pointing you to CSV.

Not yet wired: Bruno *import* (`.bru` is a text format, not JSON). Bruno *export* works.

## Verify the core

    javac -d out $(find src -name '*.java' | grep -v /burp/ | grep -v /ui/ | grep -v Extension)
    java  -cp out apicollector.demo.CoreDemo
