package ai.divyam.gradle

import org.gradle.api.Project
import java.io.File
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile

fun Project.configureGraalVmReflectionConfig() {
    tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask::class.java) {
        dependsOn("generateGraalVMConfig")
    }

    fun loadAndFilterClass(
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
        } catch (_: ClassNotFoundException) {
            println("Class not found: $className")
        } catch (_: NoClassDefFoundError) {
            println("Missing dependency for: $className")
        } catch (e: Exception) {
            println("Error loading $className: ${e.message}")
        }
    }

    fun scanDirectory(
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

    fun scanJarFile(
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

    fun findReflectableClasses(
        packageName: String,
        classLoader: URLClassLoader
    ): List<Class<*>> =
        findClassesInPackage(packageName, classLoader) { clazz ->
            clazz.annotations.any { it.annotationClass.simpleName == "Reflectable" }
        }

// Enhanced Gradle task
    tasks.register("generateGraalVMConfig") {
        dependsOn("compileKotlin")

        doLast {
            val targetPackages = listOf(
                "ai.divyam",
            )

            // Create classloader with compiled classes
            val classPathUrls =
                (configurations.getByName("runtimeClasspath").files +
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

            val discoveredClasses = mutableSetOf<Class<*>>()
            val standardClasses = mutableListOf<Class<*>>()

            listOf(
                "kotlin.collections.EmptyList",
                "kotlin.collections.EmptyMap",
                "kotlin.collections.EmptySet",
                "kotlin.reflect.KTypeProjection",
                "kotlin.reflect.KType",
                "kotlin.reflect.KClassifier",
                "kotlin.reflect.KTypeParameter"
            ).map {
                loadAndFilterClass(
                    it, classLoader, { true },
                    standardClasses
                )
            }


            discoveredClasses.addAll(standardClasses)

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

                // Jansi related
                appendLine(
                    """
                      {
                        "name": "[Lkotlin.reflect.KTypeProjection;",
                        "allDeclaredConstructors": true,
                        "allPublicConstructors": true,
                        "allDeclaredMethods": true,
                        "allPublicMethods": true
                      },
                      {
                        "name": "org.fusesource.jansi.internal.CLibrary",
                        "allDeclaredConstructors": true,
                        "allPublicConstructors": true,
                        "allDeclaredMethods": true,
                        "allPublicMethods": true
                      },
                      {
                        "name": "org.fusesource.jansi.AnsiConsole",
                        "allDeclaredConstructors": true,
                        "allPublicConstructors": true,
                        "allDeclaredMethods": true,
                        "allPublicMethods": true
                      },
                      {
                        "name": "org.fusesource.jansi.internal.WindowsSupport",
                        "allDeclaredConstructors": true,
                        "allPublicConstructors": true,
                        "allDeclaredMethods": true,
                        "allPublicMethods": true
                       },
                """.trimIndent().replaceIndent("  ")
                )

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
                            appendLine("        \"name\": \"${method.name}\"")
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

            val jniFile = file("$configDir/jni-config.json")
            jniFile.writeText(
                file(
                    "${
                        rootProject.rootDir
                            .absolutePath
                    }/buildSrc/src/main/resources/jni-config.json"
                ).readText()
            )

            val resourceFile = file("$configDir/resource-config.json")
            resourceFile.writeText(
                file(
                    "${
                        rootProject.rootDir
                            .absolutePath
                    }/buildSrc/src/main/resources/resource-config.json"
                ).readText()
            )

            println("Generated reflect-config.json at: ${configFile.absolutePath}")
        }
    }
}