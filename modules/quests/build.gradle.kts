// Quests (PHASE-10 M10.7): daily and weekly quests per profile, weekly island missions shared by an island's members,
// /quests, the Quest Master NPC. Rewards auto-claim in one database transaction with an idempotency key per quest.
dependencies {
    implementation(project(":common"))
    // Only for DepthsQuestListener (ResourceBreakEvent, DepthsMobKillEvent). compileOnly: the plugin jar shades depths
    // anyway, and quests keep working (without Depths quests in the pool) if the depths module is disabled.
    compileOnly(project(":modules:depths"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
