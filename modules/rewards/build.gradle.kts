// Rewards (PHASE-10 M10.6 + M10.5): the 30-day daily calendar per profile (/daily, streak gems and titles), active
// playtime rewards per profile (hourly crystals, 10-hour Cinder key, titles), voting (/vote, own Votifier receiver v1+v2,
// vote streak, vote party). Claims are one database
// transaction with an idempotency key; items and keys are handed out after the commit from pending grant rows.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
