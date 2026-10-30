// Islands module (Phase 2): grid, creation, home, per-player border, basic protection; later members, visits, settings.
repositories {
    // FastAsyncWorldEdit API (also mirrored by the PaperMC repo) and the WorldEdit libraries it references.
    maven("https://maven.enginehub.org/repo/") {
        content { includeGroupByRegex("com\\.sk89q.*|org\\.enginehub.*") }
    }
}

dependencies {
    implementation(project(":common"))

    // Schematic pasting: provided by the FastAsyncWorldEdit plugin at runtime (softdepend), never shaded.
    // Only FaweSchematics touches these classes, and only when the plugin is enabled.
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.16.1") { isTransitive = false }
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.1") { isTransitive = false }

    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
