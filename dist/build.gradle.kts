plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

dependencies {
    api(project(":core"))
    api(project(":ecs"))
    api(project(":binding"))
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(24) }

tasks {
    shadowJar {
        archiveBaseName.set("async")
        archiveClassifier.set("")
        mergeServiceFiles()
    }
    // Regular jar stays enabled so composite builds (examples/) can consume project(":dist") directly.
    build { dependsOn(shadowJar) }
}
