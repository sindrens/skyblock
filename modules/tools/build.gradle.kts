// Tools module (M4.2): starter kit, the Forge, living tools (levels, perk slots, prestige), blueprints, skins.
// Publishes ToolService (common) for the enchants module (Phase E).
dependencies {
    implementation(project(":common"))
    // Only for DepthsHooks (ResourceBreakEvent, ZoneService). compileOnly: the plugin jar shades depths anyway, and tools
    // keep working (without Depths XP/blueprint drops) if the depths module is disabled. Depths never depends on tools.
    compileOnly(project(":modules:depths"))
    testImplementation(project(":modules:depths"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
