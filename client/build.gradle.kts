import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureKotlin

plugins {
    java
    kotlin("jvm")
    id("org.openapi.generator") version "7.16.0"
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
    api(
        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${
            Versions
                .jackson
        }"
    )
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    sourceSets {
        getByName("main") {
            kotlin.srcDir(
                "${
                    layout.buildDirectory.get()
                        .asFile.absolutePath
                }/generated/src/main/kotlin"
            )
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-ktor")
    inputSpec.set("$projectDir/specs/openapi.json")
    outputDir.set("${layout.buildDirectory.get().asFile}/generated")
    apiPackage.set("ai.divyam.api")
    modelPackage.set("ai.divyam.data.model")
    invokerPackage.set("ai.divyam.invoker")
    templateDir.set("$projectDir/openapi-templates/ktor")

    typeMappings.set(
        mapOf(
            "number" to "kotlin.Double",
            "decimal" to "java.math.BigDecimal",
            "integer" to "kotlin.Int"
        )
    )

    configOptions.set(
        mapOf(
            "dateLibrary" to "java8",
            "serializationLibrary" to "jackson",
        )
    )
}

tasks.named("compileKotlin").get().dependsOn("openApiGenerate")