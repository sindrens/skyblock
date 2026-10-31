// Battle pass (PHASE-10 M10.8): pass XP per profile (idempotency key per source event), 100 levels + overflow, a free
// track per profile (money, crystals, tradeable keys, boosters, titles) and a cosmetic-only premium track per account
// (R3-44). Claims are one database transaction (claim rows + ledger payout + pending hand-out rows).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
