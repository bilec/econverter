pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "econverter"
include(":app")

providers.gradleProperty("bokoBindingsDir").orNull?.let { bindingsDir ->
    includeBuild(bindingsDir) {
        dependencySubstitution {
            substitute(module("io.github.bilec:boko-android")).using(project(":android"))
        }
    }
}
