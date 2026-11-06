// Shard block (ECON-SHARDS): the only source of shards. A placeable island block that pays 1 shard (sometimes 2) per
// break, regrows, and upgrades its respawn time (crystals) and double chance (shards). Payout, state change and the daily
// cap commit in one database transaction.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
