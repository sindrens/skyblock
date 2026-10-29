plugins {
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":modules:worlds"))
    implementation(project(":modules:motd"))
    implementation(project(":modules:moderation"))
    implementation(project(":modules:essentials"))
    implementation(project(":modules:social"))
    // implementation(project(":modules:islands"))
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveBaseName.set("PhoenixSkyblock")
        archiveClassifier.set("")
        // Database libraries (M0.3) are relocated so they never clash with other plugins' copies.
        val libs = "net.skybornmc.skyblock.libs"
        relocate("com.zaxxer.hikari", "$libs.hikari")
        relocate("org.mariadb.jdbc", "$libs.mariadb")
        relocate("org.h2", "$libs.h2")
        mergeServiceFiles()
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/*/module-info.class")
    }
    build { dependsOn(shadowJar) }
    jar { enabled = false }
    runServer {
        minecraftVersion("26.3")
        runDirectory.set(rootProject.layout.projectDirectory.dir("run"))
    }
}
