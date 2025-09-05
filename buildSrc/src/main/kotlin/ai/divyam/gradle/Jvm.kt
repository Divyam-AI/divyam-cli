package ai.divyam.gradle// buildSrc/src/main/kotlin/GraalVMConfig.kt

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
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
}