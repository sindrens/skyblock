// Pack module (I7 pack v2 phase 1): the forced icon pack. Status per player, prompt after join, friendly kick,
// built-in web host with a fail-open self-check, /pack status|reload. The icon API itself lives in common (Icons).
dependencies {
    implementation(project(":common"))
}

// The pack zip is built from the committed tree resourcepack/pack (tools/build_icons.py writes it) and bundled as
// pack/phoenix-icons.zip. Reproducible: fixed timestamps and sorted entries, so the SHA-1 only changes with content.
val packZip = tasks.register<Zip>("packZip") {
    from(rootProject.layout.projectDirectory.dir("resourcepack/pack"))
    archiveFileName.set("phoenix-icons.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    from(packZip) {
        into("pack")
    }
}
