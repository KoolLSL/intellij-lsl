
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile


plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    antlr
}

// Use a build folder out of OneDrive or GitHub
layout.buildDirectory.set(File("C:/IntelliJBuild/${project.name}"))

group = "kool"
sourceSets["main"].java.srcDirs("src/main/gen")
// Automatic plugin's Version (CalVer: YYYY.MM.DD.HHmm)
version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmm"))
val intellijPlatformVersion = "2026.2.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    antlr("org.antlr:antlr4:4.11.1")
    implementation("org.antlr:antlr4-runtime:4.11.1")

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        intellijIdea(intellijPlatformVersion)
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf(
        "-visitor",
        "-package", "io.github.koollsl.lsl.parser",
        "-Xexact-output-dir"
    )
    outputDirectory = file("src/main/gen")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

intellijPlatform {
    autoReload.set(true)
    buildSearchableOptions.set(false)

    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("262")
            untilBuild.set(provider { null })
        }
    }
}

tasks.test {
    doFirst {
        val disabledPluginsFile = intellijPlatform.sandboxContainer.get()
            .dir("${project.name}/IU-$intellijPlatformVersion/config-test")
            .file("disabled_plugins.txt")
            .asFile
        val disabledPlugins = disabledPluginsFile.takeIf { it.exists() }?.readLines().orEmpty()
        if ("com.intellij.modules.ultimate" !in disabledPlugins) {
            disabledPluginsFile.appendText("com.intellij.modules.ultimate\n")
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }
}

tasks.named("buildPlugin") {
    doLast {
        val zipFile = outputs.files.singleFile
        println("\n" + "=".repeat(60))
        println("📦 PLUGIN ZIP CREATED SUCCESSFULLY:")
        println("   ${zipFile.absolutePath}")
        println("\n")
    }
}

// Install plugin + restart IntelliJ
// Detect newest IntelliJ installation under %APPDATA%\JetBrains
fun detectIntelliJPluginsDir(): File {
    val jetbrainsDir = File(System.getenv("APPDATA"), "JetBrains")
    val ideaDirs = jetbrainsDir.listFiles { f ->
        f.isDirectory && f.name.startsWith("IntelliJIdea")
    } ?: emptyArray()

    val newest = ideaDirs.sortedByDescending { it.name }.firstOrNull()
        ?: error("No IntelliJ installations found in APPDATA/JetBrains")

    println("✔ Detected IntelliJ: ${newest.name}")
    return File(newest, "plugins")
}

tasks.register("installPluginToIDE") {

    group = "intellij" // Moves it into the main 'intellij' folder in the Gradle tool window
    description = "Builds, unzips, installs the plugin to local IDE, and restarts IntelliJ."

    dependsOn("buildPlugin")

    doLast {
        val zip = layout.buildDirectory.file(
            "distributions/${project.name}-${project.version}.zip"
        ).get().asFile

        val idePluginsDir = detectIntelliJPluginsDir()

        val pluginId = project.name
        val targetDir = File(idePluginsDir, pluginId)

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
            println("✔ Removed old plugin folder: $targetDir")
        }

        println("✔ Unpacking plugin ZIP into: $targetDir")

        ZipFile(zip).use { zipFile ->
            val topLevel = zipFile.entries().asIterator()
                .asSequence()
                .map { it.name.substringBefore("/") }
                .first()

            zipFile.entries().asIterator().forEach { entry ->
                val relative = entry.name.removePrefix("$topLevel/")
                if (relative.isEmpty()) return@forEach

                val outFile = File(targetDir, relative)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        println("✔ Plugin installed correctly into: $targetDir")

        // Close all IDEs gracefully. Warning: "exit" is not documented anywhere
        providers.exec {
            commandLine("C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.2.1\\bin\\idea.exe", "exit")
        }.result.get()

    }
}
