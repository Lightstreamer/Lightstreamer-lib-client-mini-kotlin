import org.jetbrains.dokka.gradle.DokkaTask

description = "Kotlin mini client"

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.dokka") version "1.9.20"
    id("maven-publish")
    id("signing")
}

allprojects {
    group = "com.lightstreamer"
    version = "0.2.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    version = parent?.version ?: error("Parent version not found")

    kotlin {
        explicitApi()
        jvmToolchain(21)
    }

    // artifact: sources
    val sourcesJar by tasks.registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    // artifact: javadoc
    val dokkaOutputDir = layout.buildDirectory.dir("dokka")
    tasks.getByName<DokkaTask>("dokkaHtml") {
        outputDirectory.set(dokkaOutputDir)
    }
    val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
        delete(dokkaOutputDir)
    }
    val javadocJar = tasks.register<Jar>("javadocJar") {
        dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaOutputDir)
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                artifact(sourcesJar)
                artifact(javadocJar)

                pom {
                    name.set(project.name)
                    description.set("Lightstreamer Kotlin mini client")
                    url.set("https://github.com/Lightstreamer/Lightstreamer-lib-client-mini-kotlin")
                    issueManagement {
                        description.set("GitHub repository")
                        system.set("github")
                        url.set("https://github.com/Lightstreamer/Lightstreamer-lib-client-mini-kotlin/issues")
                    }
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name.set("Lightstreamer Support")
                            email.set("support@lightstreamer.com")
                        }
                    }
                    scm {
                        description.set("GitHub repository")
                        connection.set("scm:git:git://github.com/Lightstreamer/Lightstreamer-lib-client-mini-kotlin.git")
                        developerConnection.set("scm:git:ssh://github.com/Lightstreamer/Lightstreamer-lib-client-mini-kotlin.git")
                        url.set("https://github.com/Lightstreamer/Lightstreamer-lib-client-mini-kotlin")
                    }
                }
            }
        }

        signing {
            useGpgCmd()
            sign(publishing.publications["mavenJava"])
        }

        publishing {
            repositories {
                maven {
                    name = "sonatype"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = System.getenv("SONATYPE_TOKEN")
                        password = System.getenv("SONATYPE_TOKEN_PASSWORD")
                    }
                }
            }
        }
    }
}
