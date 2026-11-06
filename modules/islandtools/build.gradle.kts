// Island tools (Complete Developer Spec decisions D1-D12, T1-T13): the wooden island set every profile gets, tiers wood to
// netherite and 7 crystal upgrades per tool, the hoe's rows, storage and regrowing crops.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
