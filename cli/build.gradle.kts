import ai.divyam.gradle.Versions
import ai.divyam.gradle.configureGraalVMKotlin
import ai.divyam.gradle.configurePackaging
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

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
version = "0.0.9-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    // Set this to help GraalVM find the main class
    mainClass.set("ai.divyam.cli.DivyamCliMainKt")
}

project.configureGraalVMKotlin()
project.configurePackaging()

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
    implementation("de.vandermeer:asciitable:0.3.2")

    // Tests
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Ensure task runs before any native image tasks
afterEvaluate {
    tasks.withType<org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask> {
        dependsOn("generateGraalVMConfig")
    }
}

// Enhanced Gradle task
tasks.register("generateGraalVMConfig") {
    dependsOn("compileKotlin")

    doLast {
        val targetPackages = listOf(
            "ai.divyam",
        )

        // Create classloader with compiled classes
        val classPathUrls = (configurations.runtimeClasspath.get().files +
                file(
                    "${
                        layout.buildDirectory.get().asFile
                            .absolutePath
                    }/classes/kotlin/main"
                ))
            .map {
                it.toURI().toURL()
            }
        val classLoader = URLClassLoader(classPathUrls.toTypedArray())

        val discoveredClasses = listOf(
            "kotlin.collections.EmptyList",
            "kotlin.collections.EmptyMap",
            "kotlin.collections.EmptySet"
        ).map { Class.forName(it) }.toMutableSet()

        // Find all @Serializable classes (kotlinx.serialization)
        targetPackages.forEach { packageName ->
            discoveredClasses.addAll(
                findReflectableClasses(
                    packageName,
                    classLoader
                )
            )
        }

        println("Discovered ${discoveredClasses.size} classes for GraalVM reflection:")
        discoveredClasses.forEach { println("  - ${it.name}") }

        // Generate JSON manually
        val jsonContent = buildString {
            appendLine("[")
            discoveredClasses.forEachIndexed { index, clazz ->
                appendLine("  {")
                appendLine("    \"name\": \"${clazz.name}\",")
                appendLine("    \"allDeclaredConstructors\": true,")
                appendLine("    \"allPublicConstructors\": true,")
                appendLine("    \"allDeclaredMethods\": true,")
                appendLine("    \"allPublicMethods\": true,")
                appendLine("    \"allDeclaredFields\": true,")
                append("    \"allPublicFields\": true")

                // Add explicit fields configuration
                val fields = clazz.declaredFields.filter { field ->
                    !field.isSynthetic && // Skip compiler-generated fields
                            !Modifier.isStatic(field.modifiers) // Skip static fields
                }

                if (fields.isNotEmpty()) {
                    appendLine(",")

                    appendLine("    \"fields\": [")
                    fields.forEachIndexed { fieldIndex, field ->
                        appendLine("      {")
                        appendLine("        \"name\": \"${field.name}\"")
                        if (fieldIndex < fields.size - 1) {
                            appendLine("      },")
                        } else {
                            appendLine("      }")
                        }
                    }
                    append("    ]")
                }

                // Add explicit method configuration
                val methods = clazz.declaredMethods.filter { method ->
                    !method.isSynthetic && // Skip compiler-generated methods
                            !Modifier.isStatic(method.modifiers) // Skip static methods
                }

                if (methods.isNotEmpty()) {
                    appendLine(",")

                    appendLine("    \"methods\": [")
                    methods.forEachIndexed { methodIndex, method ->
                        appendLine("      {")
                        appendLine("        \"name\": \"${method.name}\",")
                        appendLine("        \"allowWrite\": true,")
                        appendLine("        \"allowUnsafeAccess\": false")
                        if (methodIndex < methods.size - 1) {
                            appendLine("      },")
                        } else {
                            appendLine("      }")
                        }
                    }
                    append("    ]")
                }

                if (index < discoveredClasses.size - 1) {
                    appendLine()
                }

                if (index < discoveredClasses.size - 1) {
                    appendLine("  },")
                } else {
                    appendLine("  }")
                }
            }
            appendLine("]")
        }

        val configDir = file(
            "${
                layout.buildDirectory.get().asFile
                    .absolutePath
            }/native/generated"
        )
        configDir.mkdirs()

        val configFile = file("$configDir/reflect-config.json")
        configFile.writeText(jsonContent)


        println("Generated reflect-config.json at: ${configFile.absolutePath}")
    }
}

// Helper functions
fun findDataClassesInPackage(
    packageName: String,
    classLoader: URLClassLoader
): List<Class<*>> =
    findClassesInPackage(packageName, classLoader) { clazz ->
        clazz.kotlin.isData
    }

fun findReflectableClasses(
    packageName: String,
    classLoader: URLClassLoader
): List<Class<*>> =
    findClassesInPackage(packageName, classLoader) { clazz ->
        clazz.annotations.any { it.annotationClass.simpleName == "Reflectable" }
    }

fun findClassesInPackage(
    packageName: String,
    classLoader: URLClassLoader,
    filter: (Class<*>) -> Boolean
): List<Class<*>> {
    val classes = mutableListOf<Class<*>>()
    val packagePath = packageName.replace('.', '/')

    try {
        val resources = classLoader.getResources(packagePath)
        resources.asSequence().forEach { url ->
            when (url.protocol) {
                "file" -> {
                    scanDirectory(
                        url,
                        packageName,
                        classLoader,
                        filter,
                        classes
                    )
                }

                "jar" -> {
                    scanJarFile(
                        url,
                        packagePath,
                        classLoader,
                        filter,
                        classes
                    )
                }

                else -> {
                    println("Unsupported URL protocol: ${url.protocol} for $url")
                }
            }
        }
    } catch (e: Exception) {
        println("Error scanning package $packageName: ${e.message}")
    }

    return classes
}

private fun scanDirectory(
    url: URL,
    packageName: String,
    classLoader: URLClassLoader,
    filter: (Class<*>) -> Boolean,
    classes: MutableList<Class<*>>
) {
    val directory = File(url.toURI())
    if (directory.exists() && directory.isDirectory) {
        directory.walkTopDown()
            .filter { it.extension == "class" && !it.name.contains('$') }
            .forEach { classFile ->
                val relativePath = classFile.relativeTo(directory).path
                val className = "$packageName." + relativePath
                    .removeSuffix(".class")
                    .replace(File.separatorChar, '.')

                loadAndFilterClass(className, classLoader, filter, classes)
            }
    }
}

private fun scanJarFile(
    url: URL,
    packagePath: String,
    classLoader: URLClassLoader,
    filter: (Class<*>) -> Boolean,
    classes: MutableList<Class<*>>
) {
    // Extract JAR path (handle both file:// and jar:file:// URLs)
    val jarPath = when {
        url.path.startsWith("file:") -> url.path.substringBefore("!")
            .removePrefix("file:")

        else -> url.path.substringBefore("!")
    }

    var jarFile: JarFile? = null
    try {
        jarFile = JarFile(jarPath)

        jarFile.entries().asSequence()
            .filter { entry ->
                entry.name.startsWith(packagePath) &&
                        entry.name.endsWith(".class") &&
                        !entry.name.contains('$') && // Skip inner/anonymous classes
                        !entry.isDirectory
            }
            .forEach { entry ->
                val className = entry.name
                    .removeSuffix(".class")
                    .replace('/', '.')

                loadAndFilterClass(className, classLoader, filter, classes)
            }

        println("Scanned JAR: $jarPath")
    } catch (e: Exception) {
        println("Error scanning JAR file $jarPath: ${e.message}")
    } finally {
        jarFile?.close()
    }
}

private fun loadAndFilterClass(
    className: String,
    classLoader: URLClassLoader,
    filter: (Class<*>) -> Boolean,
    classes: MutableList<Class<*>>
) {
    try {
        val clazz = classLoader.loadClass(className)
        if (filter(clazz)) {
            classes.add(clazz)
            println("Found: ${clazz.simpleName}")
        }
    } catch (e: ClassNotFoundException) {
        println("Class not found: $className")
    } catch (e: NoClassDefFoundError) {
        println("Missing dependency for: $className")
    } catch (e: Exception) {
        println("Error loading $className: ${e.message}")
    }
}
