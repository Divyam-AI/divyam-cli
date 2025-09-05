import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureKotlin

plugins {
    java
    kotlin("jvm")

    // TODO: Version set from buildSrc
    kotlin("plugin.serialization") version "2.2.0"
}

group = "ai.divyam"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

project.configureKotlin()

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Ktor client
    api("io.ktor:ktor-client-core:${Versions.ktorClient}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorClient}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorClient}")

    // JSON
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorClient}")
    api(
        "com.fasterxml.jackson" +
                ".module:jackson-module-kotlin:${Versions.jackson}"
    )

    // Annotation to discover reflectable classes for Graal VM
    api("com.formkiq:graalvm-annotations:1.0.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
