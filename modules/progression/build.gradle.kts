// Progression module (Phase 5): island pillars (M5.1); later the island level, tasks, upgrades, masteries and
// leaderboards. Publishes ProgressionService (common/progression).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
