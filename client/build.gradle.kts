description = "Lightstreamer Kotlin mini client"

dependencies {
    api(project(":ls-kotlin-mini-socket"))
    testImplementation(kotlin("test"))
    testApi("org.slf4j:slf4j-simple:1.7.36")
    testApi("org.amshove.kluent:kluent:1.73")
}

tasks.test {
    useJUnitPlatform()
}
