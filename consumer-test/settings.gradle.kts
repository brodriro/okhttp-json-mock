// Standalone build, not part of the root project: it consumes the library the way an outside
// project does, from the jar and metadata published to the local Maven repository.
pluginManagement {
    val kotlinVersion: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("io.github.brodriro") } }
        mavenCentral()
    }
}

rootProject.name = "consumer-test"
