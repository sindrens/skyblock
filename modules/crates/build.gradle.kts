// Crates and keys (PHASE-10 M10.1): keys per profile (bound/tradeable), crates.yml reward tables, pure roll engine with
// pity and duplicate re-roll, /crates + preview, atomic opens (keys, pity, rolls and currency in one transaction),
// item rewards to the inventory or /box. Publishes CrateKeys (common).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
