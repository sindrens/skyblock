// Island production (PHASE-7). M7.1: Island Fuel (states Active / Fueled / Asleep, tank, burn, Fuel Cells, AFK pool fuel)
// and the fuel gate for spawners and crop growth. Publishes ProductionService (common) while production.yml → fuel.enabled
// is on, and fills IslandService#hasIslandFuel / #addIslandFuel / #islandProductionPaused through a FuelProvider.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
