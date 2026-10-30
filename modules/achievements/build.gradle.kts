// Achievements module (F3): achievements.yml catalog, progress per profile from existing events/services, reward titles
// account-wide through Cosmetics#grantTitle, the achievements menu and placeholders. Publishes AchievementService (common).
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
