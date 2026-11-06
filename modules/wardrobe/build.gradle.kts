// Wardrobe module (Phase W, docs/plans/WARDROBE.md): the 16 sets (sets.yml), per-profile wardrobe entries with locked
// real armor, the Wardrobe Chest (escrow), buying with money/crystals/credits (Sigils), Set Tokens, /wardrobe, set bonuses.
// Publishes WardrobeService (common/wardrobe).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
