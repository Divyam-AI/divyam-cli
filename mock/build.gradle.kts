import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureKotlin

plugins {
    application
    java
    kotlin("jvm")
    kotlin("kapt")
}

group = "ai.divyam"

repositories {
    mavenCentral()
}

application {
    // Set this to help GraalVM find the main class
    mainClass.set("ai.divyam.cli.MockDivyamServerKt")
}

dependencies {
    // Divyam client
    implementation(project(":divyam-client"))

    // Picocli
    implementation("info.picocli:picocli:${Versions.picocli}")
    implementation("info.picocli:picocli-jansi-graalvm:${Versions.picocliGraalVm}")
    implementation("org.fusesource.jansi:jansi:${Versions.jansi}")
    annotationProcessor("info.picocli:picocli-codegen:${Versions.picocli}")
    kapt("info.picocli:picocli-codegen:${Versions.picocli}")

    // JSON/YAML
    implementation(
        "com.fasterxml.jackson" +
                ".dataformat:jackson-dataformat-yaml:${Versions.jackson}"
    )
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}")

    implementation("org.apache.commons:commons-lang3:3.18.0")

    implementation("io.ktor:ktor-server-core:${Versions.ktorServer}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorServer}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorClient}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorServer}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorClient}")

    // Tests
    testImplementation(kotlin("test"))

}

project.configureKotlin()

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateCompletion") {
    group = "build"
    description = "Generates bash/zsh completion scripts for Picocli"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("picocli.AutoComplete")

    val outputDir =
        layout.buildDirectory.dir("generated/completion").get().asFile

    args = listOf(
        "ai.divyam.cli.DivyamCliMain", "-n", "divyam", "-o",
        File(outputDir, "divyam_completion").absolutePath
    )

    outputs.dir(outputDir)
    doFirst {
        outputDir.mkdirs()
    }
}

