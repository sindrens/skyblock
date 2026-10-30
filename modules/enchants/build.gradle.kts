// Enchants module (Phase E, docs/plans/ENCHANTS.md): enchant registry, unique enchant books + Disenchant Scrolls,
// the Enchanter (/enchanter), crystal costs, bonus families. Publishes EnchantService (common/enchant).
dependencies {
    implementation(project(":common"))
    // Only for DepthsEnchants (E2: DepthsDropEvent, DepthsMobKillEvent, the Depths block engine). compileOnly: the plugin jar
    // shades depths anyway, and enchants keep working (without Depths drops/effects) if depths is missing. Depths never
    // depends on enchants.
    compileOnly(project(":modules:depths"))
    testImplementation(project(":modules:depths"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
