package ai.divyam.gradle

import org.gradle.api.Project
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Project.configureVersionInfo() {

    fun getGitCommitHashShort(): String {
        return try {
            val stdout = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim()
        } catch (e: Exception) {
            println("Failed getting commit: ${e.message}")
            "unknown"
        }
    }

    // Simple version generation task
    tasks.register("generateVersionInfo") {
        outputs.upToDateWhen {
            return@upToDateWhen false
        }

        val outputDir = file(
            "${
                layout.buildDirectory.get()
                    .asFile
            }/generated/resources"
        )
        val propertiesFile = file("$outputDir/version.properties")

        outputs.file(propertiesFile)

        doLast {
            outputDir.mkdirs()

            val gitCommit = getGitCommitHashShort() // Use short hash
            val buildTime = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            propertiesFile.writeText(
                """
            build.version=${project.version}
            git.commit=$gitCommit
            build.time=$buildTime
        """.trimIndent()
            )
        }
    }

    // Hook into build process
    tasks.named("processResources") {
        dependsOn("generateVersionInfo")
    }
}