plugins {
    kotlin("jvm")
    application
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("busstop.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
