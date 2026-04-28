plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

dependencies {
    api(project(":core"))
    api(project(":ecs"))
    compileOnly(libs.hytale.server)

    testImplementation(libs.hytale.server)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
}
