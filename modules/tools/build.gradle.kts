// Tools module (M4.2): starter kit, the Forge, living tools (levels, perk slots, prestige), blueprints, skins.
// Publishes ToolService (common) for the enchants module (Phase E).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
