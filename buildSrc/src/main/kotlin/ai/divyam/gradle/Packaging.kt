package ai.divyam.gradle

import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingTask
import com.netflix.gradle.plugins.rpm.Rpm
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.redline_rpm.header.Os
import org.redline_rpm.header.RpmType
import java.security.MessageDigest
import java.util.Locale.getDefault

/**
 * Configure packaging of graalvm native application.
 */
fun Project.configurePackaging(
    divyamAppName: String = "divyam"
) {
    val rpmArchMap: Map<String, String> = mapOf(
        "x86" to "i386",
        "i386" to "i386",
        "i486" to "i386",
        "i586" to "i386",
        "i686" to "i386",
        "x86_64" to "x86_64",
        "amd64" to "x86_64",
        "ia64" to "ia64",
        "ppc" to "ppc",
        "ppc64" to "ppc64",
        "ppc64le" to "ppc64le",
        "s390" to "s390",
        "s390x" to "s390x",
        "aarch64" to "aarch64",
        "arm" to "armv7hl",
        "armv7l" to "armv7hl"
    ).withDefault { "noarch" }

    val debArchMap: Map<String, String> = mapOf(
        "x86" to "i386",
        "i386" to "i386",
        "i486" to "i386",
        "i586" to "i386",
        "i686" to "i386",
        "x86_64" to "amd64",
        "amd64" to "amd64",
        "ia64" to "ia64",
        "ppc" to "powerpc",
        "ppc64" to "ppc64",
        "ppc64le" to "ppc64el",
        "s390" to "s390",
        "s390x" to "s390x",
        "aarch64" to "arm64",
        "arm" to "armhf",
        "armv7l" to "armhf"
    ).withDefault { "all" }

    val companyId = "ai.divyam"
    val divyamPackageName = "${divyamAppName}-cli"
    divyamPackageName.split("-").joinToString("") {
        it.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(getDefault()) else c.toString()
        }
    }
    val sanitizedVersion = project.version.toString().replace("-SNAPSHOT", "")

    fun SystemPackagingTask.setupPackaging() {
        dependsOn("nativeCompile")
        val archMap = if (this is Deb) {
            debArchMap
        } else {
            rpmArchMap
        }
        packageName = divyamPackageName
        os = Os.LINUX
        release = "1"
        maintainer = "Divyam <divyam-devs@divyam.ai>"
        summary = "Divyam CLI."
        description = """
            A command-line application to manage Divyam.
        """.trimIndent()
        version = sanitizedVersion
        epoch = 0
        archStr =
            archMap[System.getProperty("os.arch")]?.lowercase(getDefault())
                ?: throw Exception("Unknown arch ${System.getProperty("os.arch")}")

        sourcePackage = "divyam-cli-src"

        from(tasks.named("nativeCompile")) {
            // Place the executable in the standard /usr/bin directory.
            into("/usr/bin")
            user = "root"
            group = "root"
            permissionGroup = "root"
            // Set the file permissions to be executable.
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
    }

    tasks.register("deb", Deb::class.java) {
        setupPackaging()
        archiveFileName.set(
            "${packageName}_${version}-${release}_${archStr}" +
                    ".deb"
        )
    }

    tasks.register("rpm", Rpm::class.java) {
        type = RpmType.BINARY
        setupPackaging()
        archiveFileName.set(
            "${packageName}-${version}-${release}.${archStr}" +
                    ".rpm"
        )
    }

    val projectBuildDir = layout.buildDirectory
        .get().asFile.absolutePath

    tasks.register("macPkg") {
        group = "distribution"
        description = "Creates a PKG installer for CLI tool without a custom UI"
        dependsOn("nativeCompile")

        // Determine the architecture to tag the package
        val pkgArch = when (val arch = System.getProperty("os.arch")) {
            "aarch64" -> "arm64"
            "x86_64" -> "x86_64"
            else -> throw GradleException("Unsupported architecture: $arch")
        }

        val pkgFile =
            file(
                "$projectBuildDir/distributions/${divyamPackageName}-${sanitizedVersion}" +
                        ".$pkgArch.pkg"
            )

        pkgFile.parentFile.mkdirs()

        doLast {
            // Prepare staging folder with only the CLI binary
            val binary =
                file("$projectBuildDir/native/nativeCompile/$divyamAppName")
            val tempDir = file("$projectBuildDir/pkg-temp")
            val binDir = file("${tempDir}/usr/local/bin")

            tempDir.deleteRecursively()
            tempDir.mkdirs()
            binDir.mkdirs()

            copy {
                from(binary)
                into(binDir)
            }

            // Create the component package
            @Suppress("DEPRECATION")
            exec {
                commandLine(
                    "pkgbuild",
                    "--root", tempDir.absolutePath,
                    "--identifier", "${companyId}.${divyamPackageName}",
                    "--version", sanitizedVersion,
                    pkgFile.absolutePath
                )
            }

            // Cleanup temp files
            tempDir.deleteRecursively()

            println("PKG created at: $pkgFile")
        }
    }

    val brewPackageDist = tasks.register("brewPackageDist", Tar::class.java) {
        dependsOn("nativeCompile")

        group = "distribution"
        description = "Packages binary, LICENSE, and README.md into a tar.gz"

        archiveBaseName.set(divyamPackageName)
        archiveVersion.set(sanitizedVersion)
        archiveClassifier.set("mac-${System.getProperty("os.arch")}")
        archiveExtension.set("tar.gz")

        compression = Compression.GZIP

        from(tasks.named("nativeCompile")) {
            include(divyamAppName) // your binary file name
        }

        // Include LICENSE from project root
        println(rootDir)
        from(rootDir) {
            include("LICENSE")
        }

        // Make sure binary is executable inside tar.gz
        filesMatching(divyamAppName) {
            permissions {
                unix(0b111101101)
            }
        }

        destinationDirectory.set(
            layout.buildDirectory.dir
                ("distributions/homebrew")
        )
    }

    tasks.register("generateBrewFormula") {
        dependsOn(brewPackageDist)

        group = "distribution"
        description = "Generate Homebrew formula for this project"

        val formulaName = divyamPackageName // Capitalized class name
        val binaryName = divyamAppName
        val versionString = project.version.toString()
        val archiveFile = brewPackageDist.flatMap { it.archiveFile }

        val repoUrl = "https://github.com/divyam/$divyamPackageName"
        val releaseUrl =
            "$repoUrl/releases/download/v$versionString/${
                archiveFile.get()
                    .asFile.name
            }"

        outputs.file(
            layout.buildDirectory.file
                (
                "distributions/homebrew/formula/${
                    archiveFile.get().asFile
                        .name.replace(".tar.gz", "")
                }.rb"
            )
        )

        doLast {
            // Compute sha256 if file exists
            val sha256 = if (archiveFile.get().asFile.exists()) {
                val bytes = archiveFile.get().asFile.readBytes()
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            } else {
                "PUT_SHA256_HERE"
            }

            val formula = """
            class $formulaName < Formula
              desc "Divyam CLI"
              homepage "$repoUrl"
              url "$releaseUrl"
              sha256 "$sha256"
              version "$versionString"

              def install
                bin.install "$binaryName"
              end

              test do
                system "#{bin}/$binaryName", "--help"
              end
            end
        """.trimIndent()

            val outputFile = outputs.files.singleFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(formula)

            println("Generated Homebrew formula at: ${outputFile.absolutePath}")
        }
    }

    tasks.register("generateBrewFormulaLocal") {
        dependsOn(brewPackageDist)

        group = "distribution"
        description = "Generate Homebrew formula for this project"

        // Capitalized class name
        val formulaName = divyamPackageName.split("-").joinToString("") { str ->
            str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
        }

        val versionString = project.version.toString()
        val archiveFile = brewPackageDist.flatMap { it.archiveFile }

        val repoUrl = "https://github.com/divyam/$divyamPackageName"
        val releaseUrl = "file://#{Pathname.pwd}/${
            archiveFile.get()
                .asFile.name
        }"

        outputs.file(
            layout.buildDirectory.file
                (
                "distributions/homebrew/${divyamPackageName}.local.rb"
            )
        )

        doLast {
            // Compute sha256 if file exists
            val sha256 = if (archiveFile.get().asFile.exists()) {
                val bytes = archiveFile.get().asFile.readBytes()
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            } else {
                "PUT_SHA256_HERE"
            }

            val formula = """
            class $formulaName < Formula
              desc "Divyam CLI"
              homepage "$repoUrl"
              url "$releaseUrl"
              sha256 "$sha256"
              version "$versionString"

              def install
                bin.install "$divyamAppName"
              end

              test do
                system "#{bin}/$divyamAppName", "--help"
              end
            end
        """.trimIndent()

            val outputFile = outputs.files.singleFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(formula)

            println("Generated Homebrew formula at: ${outputFile.absolutePath}")
        }
    }
}