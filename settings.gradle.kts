pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

// The modules compile with a Java 17 toolchain. Provision it when the machine
// runs a different JDK, so the build works without a hand-installed JDK 17.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jisho-lens"

include(":dictionary")
include(":cli")
include(":app")
