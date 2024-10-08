description = "TLCP parser"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    api("io.github.microutils:kotlin-logging-jvm:3.0.5")
    testImplementation(kotlin("test"))
    testApi("ch.qos.logback:logback-classic:1.2.13")
    testApi("org.amshove.kluent:kluent:1.73")
}

tasks.test {
    useJUnitPlatform()
}
