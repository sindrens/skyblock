// Collection log (PHASE-10 M10.10, /collection): per profile, an entry unlocks the first time the profile gets the item
// itself (Depths drops and mob drops, crate star rewards; later fishing, bosses ...). Page milestones feed the island
// level ("Collections" source); a full page and the whole log pay titles and keys through crash-safe grant rows.
dependencies {
    implementation(project(":common"))
    // Only for the Depths hooks (DepthsDropEvent, DepthsMobKillEvent). compileOnly: the plugin jar shades depths anyway, and
    // the log keeps working (without Depths finds) if depths is missing. Depths never depends on the collection log.
    compileOnly(project(":modules:depths"))
    testImplementation(project(":modules:depths"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
