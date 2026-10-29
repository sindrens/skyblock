plugins {
    java
    id("com.gradleup.shadow") version "8.3.9" apply false
    id("xyz.jpenilla.run-paper") version "2.3.1" apply false
}

val paperVersion = "1.21.11-R0.1-SNAPSHOT"

allprojects {
    group = "dev.slickymatt.skyblock"
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
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
