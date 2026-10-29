// Shared framework used by the plugin and every feature module.
dependencies {
    // Database (M0.3): shaded + relocated into the plugin jar (see plugin/build.gradle.kts).
    // slf4j is provided by Paper at runtime, so it is never shaded.
    api("com.zaxxer:HikariCP:7.1.0") { exclude(group = "org.slf4j") }
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.11") { exclude(group = "org.slf4j") }
    implementation("com.h2database:h2:2.5.252")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
}
