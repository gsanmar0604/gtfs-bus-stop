plugins {
    kotlin("jvm")
    application
    jacoco
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
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// Run from the repo root so relative paths like data/bizkaibus.zip work as documented.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Builds a minimal GTFS subset zip for the Android app (only stop TARGET_STOP).
// Skipped when the real feed is absent so CI and cold checkouts still build.
val buildFeedSubset by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("busstop.SubsetMainKt")
    val sourceZip = rootProject.file("data/bizkaibus.zip")
    val targetZip = layout.buildDirectory.file("gtfsSubset/bizkaibus.zip")
    args(sourceZip.absolutePath, targetZip.get().asFile.absolutePath)
    inputs.file(sourceZip).optional()
    outputs.file(targetZip)
    onlyIf { sourceZip.exists() }
}
