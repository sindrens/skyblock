// Shared framework used by the plugin and every feature module.
dependencies {
    // Database (M0.3): shaded + relocated into the plugin jar (see plugin/build.gradle.kts).
    // slf4j is provided by Paper at runtime, so it is never shaded.
    api("com.zaxxer:HikariCP:7.1.0") { exclude(group = "org.slf4j") }
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.11") { exclude(group = "org.slf4j") }
    implementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")

    // Optional integrations (M0.9): provided by the server when installed, never shaded.
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
}

// Icon pack v2 (I7, GUI-P1-PACK D2): the one catalogue resourcepack/icons.json is bundled as icons/icons.json, so the
// runtime (IconCatalog), the unit tests and tools/build_icons.py all read the same file.
tasks.processResources {
    from(rootProject.layout.projectDirectory.file("resourcepack/icons.json")) {
        into("icons")
    }
}

// Frame layer (GUI-FONT 4.3): the generated metrics (tools/build_panels.py pack) are bundled as frame/frame-metrics.json,
// so the Java title builder never hard-codes a pixel.
tasks.processResources {
    from(rootProject.layout.projectDirectory.file("resourcepack/frame/frame-metrics.json")) {
        into("frame")
    }
}
