// Builder shop module (M3.5): /shop categories, item pages, buy 1/16/64/custom, one ledger transaction per purchase.
dependencies {
    implementation(project(":common"))
    // The buy > sell invariant test reads the real sell.yml and economy defaults.
    testImplementation(project(":modules:economy"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
