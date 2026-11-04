// The guided tour /tutorial (docs/planning/PLAN_TUTORIAL_CHAT.md section 3, PLAN_4NOV_C.md section 4): seven chapters
// from tutorial.yml, a quest book (chest menu on Bedrock), the boss bar and scoreboard "Next" step, talking NPCs at spawn
// and the pages of Rowan's /start menu. Progress and rewards per account.
dependencies {
    implementation(project(":common"))
    // Unit tests run the real SQL against a throw-away H2 database (MySQL mode, like the local test server).
    testImplementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
