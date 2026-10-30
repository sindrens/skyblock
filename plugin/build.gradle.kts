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
    implementation(project(":modules:hud"))
    implementation(project(":modules:staff"))
    implementation(project(":modules:social"))
    implementation(project(":modules:perks"))
    implementation(project(":modules:chat"))
    implementation(project(":modules:menus"))
    implementation(project(":modules:logs"))
    implementation(project(":modules:cosmetics"))
    implementation(project(":modules:depths"))
    implementation(project(":modules:economy"))
    implementation(project(":modules:skills"))
    // implementation(project(":modules:islands"))
    implementation(project(":modules:islands"))
    implementation(project(":modules:auction"))
    implementation(project(":modules:stacking"))
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
