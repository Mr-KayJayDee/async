plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.jdk8)
    api(libs.slf4j.api)

    compileOnly(libs.hytale.server)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.slf4j.simple)
}
