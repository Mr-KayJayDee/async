plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

group = "com.mythlane.example"
version = "0.1.0"
description = "Async example: periodic-leaderboard"

val hytaleServerVersion = libs.versions.hytaleServer.get()

dependencies {
    compileOnly(libs.hytale.server)
    implementation("com.mythlane:async:0.1.0")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(24) }

tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()
    val props = mapOf(
        "version" to project.version,
        "description" to (project.description ?: ""),
        "hytaleServerVersion" to hytaleServerVersion,
    )
    inputs.properties(props)
    filesMatching("manifest.json") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set(project.name)
    archiveClassifier.set("")
}
tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
