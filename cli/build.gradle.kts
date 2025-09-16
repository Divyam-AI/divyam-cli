import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureGraalVMKotlin
import ai.divyam.gradle.configureGraalVmReflectionConfig
import ai.divyam.gradle.configurePackaging
import ai.divyam.gradle.configureVersionInfo

plugins {
    application
    java
    kotlin("jvm")
    kotlin("kapt")
    id("org.graalvm.buildtools.native")

    // TODO: Version set from buildSrc
    kotlin("plugin.serialization") version "2.2.0"
}

group = "ai.divyam"

repositories {
    mavenCentral()
}

application {
    // Set this to help GraalVM find the main class
    mainClass.set("ai.divyam.cli.DivyamCliMainKt")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")

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

    // Ascii Tables
    implementation("de.vandermeer:asciitable:0.3.2") {
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }

    implementation("org.apache.commons:commons-lang3:3.18.0")

    // Tests
    testImplementation(kotlin("test"))
}

// Setup the project.
project.configureGraalVMKotlin()
project.configurePackaging()
project.configureGraalVmReflectionConfig()
project.configureVersionInfo()



sourceSets {
    main {
        resources.srcDir("${layout.buildDirectory.get().asFile}/generated/resources")
    }
}

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
