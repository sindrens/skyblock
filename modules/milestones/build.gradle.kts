// Milestones (Complete Developer Spec §7-9, /milestones and /ms): grind an activity, earn tiers, claim perks that speed up
// that same activity. Island chains are shared by the island's members; personal chains belong to one profile.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
