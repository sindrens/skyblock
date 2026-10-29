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
        // PlaceholderAPI (compileOnly, optional integration).
        maven("https://repo.extendedclip.com/releases/") {
            content { includeGroup("me.clip") }
        }
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

// F2: docs/COMMANDS.md is an index of docs/commands/<module>.md. Regenerates the table between the markers:
// core first, then modules alphabetically, then the planned list. Title = the file's first "# " heading.
tasks.register("commandsIndex") {
    group = "documentation"
    description = "Regenerates the module table in docs/COMMANDS.md from docs/commands/*.md"
    val dir = layout.projectDirectory.dir("docs/commands").asFile
    val index = layout.projectDirectory.file("docs/COMMANDS.md").asFile
    doLast {
        val begin = "<!-- BEGIN GENERATED INDEX (./gradlew commandsIndex) -->"
        val end = "<!-- END GENERATED INDEX -->"
        val files = (dir.listFiles { f -> f.isFile && f.name.endsWith(".md") } ?: emptyArray()).toList()
        fun rank(name: String) = when (name) { "core" -> 0; "planned" -> 2; else -> 1 }
        val rows = files.map { it.nameWithoutExtension to it }
            .sortedWith(compareBy({ rank(it.first) }, { it.first }))
            .map { (name, file) ->
                val title = file.readLines().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: name
                "| [$name](commands/${file.name}) | $title |"
            }
        val table = listOf("| Module | Contents |", "|---|---|") + rows
        val text = index.readText()
        val start = text.indexOf(begin)
        val stop = text.indexOf(end)
        require(start >= 0 && stop > start) { "Markers not found in docs/COMMANDS.md" }
        val updated = text.substring(0, start + begin.length) + "\n" + table.joinToString("\n") + "\n" + text.substring(stop)
        if (updated != text) index.writeText(updated)
        println("docs/COMMANDS.md: ${rows.size} command file(s) indexed.")
    }
}
