package ai.divyam.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

/**
 * Configure GraalVM Native Image compilation for Kotlin projects
 */
fun Project.configureGraalVMKotlin(
    javaVersion: Int = 21,
    binaryName: String = "divyam",
    additionalBuildArgs: List<String> = emptyList()
) {
    // Configure Kotlin toolchain
    extensions.configure<KotlinJvmProjectExtension>("kotlin") {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
            vendor.set(JvmVendorSpec.GRAAL_VM)
        }
    }

    // Configure Java toolchain
    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
            vendor.set(JvmVendorSpec.GRAAL_VM)
        }
    }

    afterEvaluate {
        val toolChain =
            extensions.getByType<JavaToolchainService>()

        // Fix GraalVM permissions
        tasks.register("fixGraalVMPermissions") {
            doLast {
                val graalvmHome =
                    toolChain.launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(javaVersion))
                        vendor.set(JvmVendorSpec.GRAAL_VM)
                    }.get().metadata.installationPath.asFile

                println("Fixing GraalVM permissions in: ${graalvmHome.absolutePath}")

                // Fix all binary permissions
                project.fileTree(graalvmHome) {
                    include("**/bin/*")
                }.forEach { file ->
                    file.setExecutable(true)
                }

                // Fix empty native-image binary
                val nativeImage = File(graalvmHome, "bin/native-image")
                if (nativeImage.exists() && nativeImage.length() == 0L) {
                    println("native-image is empty, attempting to restore...")
                    val libNativeImage =
                        File(graalvmHome, "lib/svm/bin/native-image")
                    if (libNativeImage.exists()) {
                        nativeImage.delete()
                        libNativeImage.copyTo(nativeImage, overwrite = true)
                        nativeImage.setExecutable(true)
                        println("Restored native-image from lib/svm/bin/")
                    }
                }
            }
        }
    }

    // Configure GraalVM Native
    extensions.configure<GraalVMExtension>("graalvmNative") {
        binaries {
            all {
                javaLauncher.set(
                    extensions.getByType<JavaToolchainService>().launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(javaVersion))
                        vendor.set(JvmVendorSpec.GRAAL_VM)
                    })
            }

            named("main") {
                configurationFileDirectories.from(
                    file(
                        "${
                            layout.buildDirectory.get().asFile
                                .absolutePath
                        }/native/generated"
                    )
                )

                // Default Kotlin-optimized build args
                val defaultBuildArgs = listOf(
                    "--no-fallback",
                    "--enable-preview",

                    // Kotlin-specific configurations
                    "--initialize-at-build-time=kotlin",
                    "--initialize-at-build-time=kotlinx",
                    "--initialize-at-build-time=kotlin.jvm.internal",
                    "--initialize-at-build-time=kotlin.reflect",

                    "--initialize-at-build-time=ch.qos.logback",
                    "--initialize-at-build-time=io.ktor,kotlin",
                    "--initialize-at-build-time=org.slf4j.LoggerFactory",

                    "--initialize-at-build-time=org.slf4j.helpers.Reporter",
                    "--initialize-at-build-time=kotlinx.io.bytestring.ByteString",
                    "--initialize-at-build-time=kotlinx.io.SegmentPool",

                    "--initialize-at-build-time=kotlinx.serialization",
                    "--initialize-at-build-time=kotlinx.serialization.json",
                    "--initialize-at-build-time=kotlinx.serialization.json.Json",
                    "--initialize-at-build-time=kotlinx.serialization.json.JsonImpl",
                    "--initialize-at-build-time=kotlinx.serialization.json.ClassDiscriminatorMode",
                    "--initialize-at-build-time=kotlinx.serialization.modules.SerializersModuleKt",

                    "--initialize-at-build-time=org.fusesource.jansi.internal" +
                            ".CLibrary",
                    "--initialize-at-build-time=org.fusesource.jansi.internal.OSInfo",

                    "-H:+AddAllCharsets",
                    "-H:+UnlockExperimentalVMOptions",
                    "-H:IncludeResources=org/fusesource/jansi/internal/native" +
                            "/.*",

                    // Memory and performance
                    "-H:+ReportExceptionStackTraces",
                    "--enable-url-protocols=http,https",
                    "-O3" // Additional compiler optimizations
                )

                val osSpecificArgs: List<String> =
                    if (OperatingSystem.current().isLinux) {
                        listOf("--static")
                    } else {
                        emptyList()
                    }

                buildArgs.addAll(defaultBuildArgs + additionalBuildArgs + osSpecificArgs)
                imageName.set(binaryName)
                verbose.set(true)
                debug.set(project.hasProperty("debug"))
            }

            named("test") {
                quickBuild.set(true)
                debug.set(true)
            }
        }
    }

    // Hook up task dependencies
    tasks.named("nativeCompile") {
        dependsOn("fixGraalVMPermissions")
        dependsOn(tasks.named("compileKotlin"))

        doLast {
            val binary = outputs.files.singleFile
            println("Native binary created: ${binary.absolutePath}")
            println("Binary size: ${binary.length() / 1024 / 1024}MB")
        }
    }

    tasks.named("nativeRun") {
        dependsOn("fixGraalVMPermissions")
    }

    tasks.named("nativeTestCompile") {
        dependsOn("fixGraalVMPermissions")
    }

    // Configure Kotlin compiler for GraalVM compatibility
    tasks.named(
        "compileKotlin",
        KotlinCompile::class
    ) {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            freeCompilerArgs.set(
                listOf(
                    "-Xjsr305=strict",
                    "-Xjvm-default=all",
                    "-Xopt-in=kotlin.RequiresOptIn"
                )
            )
        }
    }
}