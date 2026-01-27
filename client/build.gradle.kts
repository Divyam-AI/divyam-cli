import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureKotlin
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ArrayNode
import java.nio.file.Files
import java.nio.file.Path
import java.io.File

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
            resources.srcDir(
                "${
                    layout.buildDirectory.get()
                        .asFile.absolutePath
                }/generated/src/main/resources"
            )
        }
    }
}

private val PRIMITIVE_TYPES = setOf(
    "string",
    "integer",
    "number",
    "boolean"
)

fun preprocessOpenApiNullableAnyOf(
    inputSpec: Path,
    outputSpec: Path
) {
    val mapper = ObjectMapper()
    val root = mapper.readTree(inputSpec.toFile())

    fun rewrite(node: JsonNode) {
        when {
            node.isObject -> {
                val obj = node as ObjectNode

                val anyOf = obj.get("anyOf")
                if (anyOf is ArrayNode && anyOf.size() == 2) {
                    val types = anyOf.mapNotNull { it.get("type")?.asText() }.toSet()

                    if ("null" in types && types.size == 2) {
                        val primitive = (types - "null").firstOrNull()
                        if (primitive in PRIMITIVE_TYPES) {
                            obj.remove("anyOf")
                            obj.put("type", primitive)
                            obj.put("nullable", true)
                        }
                    }
                }

                obj.elements().forEachRemaining { value ->
                    rewrite(value)
                }
            }

            node.isArray -> node.forEach { rewrite(it) }
        }
    }

    rewrite(root)

    Files.createDirectories(outputSpec.parent)
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(outputSpec.toFile(), root)
}

/* ============================================================
   Preprocess task
   ============================================================ */

val fixedOpenApiSpec =
    layout.buildDirectory.file("openapi/openapi.fixed.json")

tasks.register("preprocessOpenApi") {
    inputs.file("$projectDir/specs/openapi.json")
    outputs.file(fixedOpenApiSpec)

    doLast {
        preprocessOpenApiNullableAnyOf(
            inputSpec = file("$projectDir/specs/openapi.json").toPath(),
            outputSpec = fixedOpenApiSpec.get().asFile.toPath()
        )
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-ktor")
    inputSpec.set(fixedOpenApiSpec.get().asFile.absolutePath)
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

tasks.register("generateModelIndex") {
    // Ensure we run after the generator but before resources are processed
    dependsOn("openApiGenerate", "compileJava")

    val modelDir =
        file(
            "${
                layout.buildDirectory.get()
                    .asFile
            }/generated/src/main/kotlin"
        )
    val outputFile = file(
        "${
            layout.buildDirectory.get()
                .asFile
        }/generated/src/main/resources/model-index.txt"
    )

    inputs.dir(modelDir)
    outputs.file(outputFile)

    doLast {
        val classes = mutableListOf<String>()
        // Use walk() to go into every subdirectory
        modelDir.walk().forEach { file ->
            val className =
                file.toRelativeString(modelDir).replace(".kt", "").replace(
                    File
                        .separatorChar,
                    '.'
                )
            if (file.isFile && file.extension == "kt") {
                classes.add(className)
            }
        }
        outputFile.writeText(classes.distinct().joinToString("\n"))
        println("Generated index with ${classes.size} classes for GraalVM reflection.")
    }
}

tasks.named("processResources") {
    dependsOn("generateModelIndex")
}

tasks.named("openApiGenerate") {
    dependsOn("preprocessOpenApi")
}

tasks.named("compileKotlin").get().dependsOn("openApiGenerate")