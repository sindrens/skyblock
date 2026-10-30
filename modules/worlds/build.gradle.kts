// Worlds module: creates and configures the server's worlds (spawn, islands, outposts, depths, warzone) and pastes /
// wires the finished builds (/skyblock builds).
repositories {
    // FastAsyncWorldEdit API and the WorldEdit libraries it references.
    maven("https://maven.enginehub.org/repo/") {
        content { includeGroupByRegex("com\\.sk89q.*|org\\.enginehub.*") }
    }
}

dependencies {
    implementation(project(":common"))

    // Build pasting: provided by the FastAsyncWorldEdit plugin at runtime (softdepend), never shaded.
    // Only FaweBuildPaste touches these classes, and only when the plugin is enabled.
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.16.1") { isTransitive = false }
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.16.1") { isTransitive = false }
}

// The build tests read the tracked builds in server/builds: rerun them when a build changes.
tasks.test { inputs.dir(rootProject.file("server/builds")).withPropertyName("trackedBuilds") }
