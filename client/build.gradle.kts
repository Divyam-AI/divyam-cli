import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureKotlin
import com.pswidersk.gradle.python.VenvTask

plugins {
    java
    kotlin("jvm")

    id("com.pswidersk.python-plugin")
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
}

tasks.test {
    useJUnitPlatform()
}

pythonPlugin {
    pythonVersion = "3.13.3"
}

tasks.register<VenvTask>("generateDataModels") {

    val libsDir = System.getenv("DIVYAM_LIBS_DIR") ?: "${
        project.rootDir.absolutePath
    }/../divyam_python_libs"
    val apiDir = System.getenv("DIVYAM_API_DIR") ?: "${
        project.rootDir.absolutePath
    }/../divyam_router_controller"
    workingDir = projectDir.resolve("data-model-sync/src")
    val destDir = "${
        project.projectDir
            .absolutePath
    }/src/main/kotlin/ai/divyam/client/data/models"

    args =
        listOf(
            "generate_kotlin_models.py", "--libs", libsDir, "--api",
            apiDir, "--dest", destDir
        )
}
