// Secure trade module (M3.7): /trade requests, two-sided trade menu, database item escrow, one atomic swap.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
