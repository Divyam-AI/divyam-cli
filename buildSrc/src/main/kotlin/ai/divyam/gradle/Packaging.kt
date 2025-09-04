package ai.divyam.gradle

import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingTask
import com.netflix.gradle.plugins.rpm.Rpm
import org.gradle.api.Project
import org.redline_rpm.header.Os
import org.redline_rpm.header.RpmType
import java.util.Locale.getDefault

/**
 * Configure packaging of graalvm native application.
 */
fun Project.configurePackaging(
    appName: String = "divyam"
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
        packageName = "${appName}-cli"
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

    afterEvaluate {
        tasks.register("deb", Deb::class.java) {
            setupPackaging()
        }

        tasks.register("rpm", Rpm::class.java) {
            type = RpmType.BINARY
            setupPackaging()
        }
    }
}