// Stacking module: stacked mobs, items and spawners, island entity limits, crops that grow without light.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real spawner SQL against a throw-away H2 database (MySQL mode).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
