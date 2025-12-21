package ai.divyam.gradle// buildSrc/src/main/kotlin/GraalVMConfig.kt

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Configure GraalVM Native Image compilation for Kotlin projects
 */
fun Project.configureKotlin(
    javaVersion: Int = 21,
) {
    // Configure Kotlin toolchain
    extensions.configure<KotlinJvmProjectExtension>("kotlin") {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
    }

    // Configure Java toolchain
    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            allWarningsAsErrors.set(true)
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Werror")  // treat warnings as errors
        options.compilerArgs.add("-Xlint:all") // enable all lint warnings
        options.release.set(javaVersion)
    }
}