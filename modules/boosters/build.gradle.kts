// Boosters (PHASE-10 M10.2): personal booster items (bound, per profile, right-click, same type adds time), banked time
// (the AFK pool), global boosters with a queue and boss bar, /boosters, /thanks (1 earned credit to the starter, RW2). Every booster
// goes through BonusService, so the family caps and the +300% sell cap apply (O11). Publishes BoosterService (common).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
