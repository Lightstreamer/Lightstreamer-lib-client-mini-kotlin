plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.dokka") version "1.9.20"
}

allprojects {
    group = "com.lightstreamer"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    version = parent?.version ?: error("Parent version not found")

    kotlin {
        explicitApi()
        jvmToolchain(21)
    }
}
