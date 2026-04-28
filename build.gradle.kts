plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

group = findProperty("libGroup") as String? ?: "com.mythlane"
version = findProperty("libVersion") as String? ?: "0.1.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(24)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            jvmArgs("-Dnet.bytebuddy.experimental=true")
        }
    }
}
