// AFK pool module (O10, RW3): rewards for standing in the pool at spawn (money, island fuel, crate keys, a banked
// personal sell booster), daily cap per account, one earning account per IP.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
