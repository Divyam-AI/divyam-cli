package ai.divyam.gradle

import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingTask
import com.netflix.gradle.plugins.rpm.Rpm
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.redline_rpm.header.Os
import org.redline_rpm.header.RpmType
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

    fun SystemPackagingTask.setupPackaging() {
        dependsOn("nativeCompile")
        val archMap = if (this is Deb) {
            debArchMap
        } else {
            rpmArchMap
        }
        packageName = "${divyamAppName}-cli"
        os = Os.LINUX
        release = "1"
        maintainer = "Divyam <divyam-devs@divyam.ai>"
        summary = "Divyam CLI."
        description = """
            A command-line application to manage Divyam.
        """.trimIndent()
        version = project.version.toString().replace("-SNAPSHOT", "")
        epoch = 0
        archStr =
            archMap[System.getProperty("os.arch")]?.uppercase(getDefault())
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
    }

    tasks.register("rpm", Rpm::class.java) {
        type = RpmType.BINARY
        setupPackaging()
    }

    val projectBuildDir = layout.buildDirectory
        .get().asFile.absolutePath
    val macAppName = "${divyamAppName}-cli"

    tasks.register("macAppBundle", Copy::class.java) {
        dependsOn("nativeCompile")

        group = "distribution"
        description = "Creates macOS app bundle structure"
        dependsOn("installDist")

        val bundleDir = file(
            "$projectBuildDir/distributions/macos/${macAppName}.app"
        )

        doFirst {
            bundleDir.deleteRecursively()
            bundleDir.mkdirs()

            // Create app bundle structure
            file("${bundleDir}/Contents/MacOS").mkdirs()
            file("${bundleDir}/Contents/Resources").mkdirs()
        }

        // Copy application files
        from(file("$projectBuildDir/native/nativeCompile/$divyamAppName"))
        into("${bundleDir}/Contents/MacOS")

        doLast {
            // Create Info.plist
            val infoPlist = file("${bundleDir}/Contents/Info.plist")

            infoPlist.writeText(
                """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>CFBundleDevelopmentRegion</key>
            |    <string>en</string>
            |    <key>CFBundleExecutable</key>
            |    <string>$divyamAppName</string>
            |    <key>CFBundleIdentifier</key>
            |    <string>ai.divyam.${
                    macAppName.lowercase()
                }</string>
            |    <key>CFBundleInfoDictionaryVersion</key>
            |    <string>6.0</string>
            |    <key>CFBundleName</key>
            |    <string>$macAppName</string>
            |    <key>CFBundlePackageType</key>
            |    <string>APPL</string>
            |    <key>CFBundleShortVersionString</key>
            |    <string>${project.version}</string>
            |    <key>CFBundleVersion</key>
            |    <string>${project.version}</string>
            |    <key>LSMinimumSystemVersion</key>
            |    <string>10.14</string>
            |    <key>NSHighResolutionCapable</key>
            |    <true/>
            |</dict>
            |</plist>
        """.trimMargin()
            )

            // Make executable
            exec {
                commandLine(
                    "chmod",
                    "+x",
                    "${bundleDir}/Contents/MacOS/$divyamAppName"
                )
            }

            println("App bundle created at: $bundleDir")
        }
    }

    // Simple task to zip the app bundle
    tasks.register("macAppBundleZip", Zip::class.java) {
        group = "distribution"
        description = "Creates a ZIP archive of the app bundle"

        dependsOn("macAppBundle")

        val version = project.version

        from(file("$projectBuildDir/distributions/macos/"))
        include("${macAppName}.app/**")

        archiveFileName.set(
            "${macAppName}-${
                version.toString().replace
                    ("-SNAPSHOT", "")
            }-mac.zip"
        )
        destinationDirectory.set(file("build/distributions"))

        doLast {
            println("App ZIP created at: ${destinationDirectory.get().asFile}/${archiveFileName.get()}")
        }
    }
}