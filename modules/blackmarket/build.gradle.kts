// Black Market (PHASE-10 M10.11, /blackmarket): rotating rare offers paid in shards (Island Charter also in gems, REVIEW3
// F6). Each buy is one database transaction: currency out, limit and stock counted, a buy row written; the hand-out
// follows (pending -> sending -> delivered, crash -> review), so an item can be lost but never doubled.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
