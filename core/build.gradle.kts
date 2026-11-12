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

// Run from the repo root so relative paths like data/bizkaibus.zip work as documented.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
