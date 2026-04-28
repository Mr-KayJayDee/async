rootProject.name = "player-load"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.hytale.com/release") { name = "hytale" }
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.mythlane:async"))
            .using(project(":dist"))
    }
}
