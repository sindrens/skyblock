plugins {
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":common"))
    // implementation(project(":modules:islands"))
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveBaseName.set("Skyblock")
        archiveClassifier.set("")
    }
    build { dependsOn(shadowJar) }
    jar { enabled = false }
    runServer {
        minecraftVersion("26.3")
        runDirectory.set(rootProject.layout.projectDirectory.dir("run"))
    }
}
