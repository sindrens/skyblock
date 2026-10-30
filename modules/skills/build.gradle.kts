// Skills module: skill XP and levels (M4.5), /skills. Publishes SkillService (common) for tiers, tools and perks.
dependencies {
    implementation(project(":common"))
    // Only for DepthsXpListener (ResourceBreakEvent). compileOnly: the plugin jar shades depths anyway, and skills must
    // keep working (without Depths XP) if the depths module is disabled. Depths must never depend on skills.
    compileOnly(project(":modules:depths"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
