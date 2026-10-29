plugins {
    java
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

// Minecraft 26.3. Bump the build number when updating Paper.
val paperVersion = "26.3.build.230-stable"

allprojects {
    group = "net.skybornmc.skyblock"
    version = "0.1.0-SNAPSHOT"

    repositories {
        // PaperMC's repo also proxies Maven Central.
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:$paperVersion")
        "testImplementation"("io.papermc.paper:paper-api:$paperVersion")
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
