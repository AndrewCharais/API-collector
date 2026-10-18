plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "apicollector"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.10")

    // Bundled into the extension jar (Burp does not provide these):
    implementation("org.yaml:snakeyaml:2.3")                 // OpenAPI/Swagger YAML import
    implementation("org.apache.poi:poi-ooxml:5.3.0")         // Excel .xlsx import
}

tasks.shadowJar {
    archiveBaseName.set("api-collector")
    archiveClassifier.set("")   // produce api-collector.jar (no "-all" suffix)
}

// Make `jar` build the fat jar, so ./gradlew jar or build both give a loadable extension.
tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
