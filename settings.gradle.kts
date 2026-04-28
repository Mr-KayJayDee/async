rootProject.name = "hytale-async"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "hytale"
            url = uri("https://maven.hytale.com/release")
        }
    }
}

include("core", "ecs", "binding", "dist")
