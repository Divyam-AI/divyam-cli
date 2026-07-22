plugins {
    id("com.diffplug.spotless") version "6.25.0"
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        java {
            target("src/**/*.java")
            licenseHeaderFile(rootProject.file("config/spotless/copyright-header.txt"))
        }
        kotlin {
            target("src/**/*.kt")
            licenseHeaderFile(rootProject.file("config/spotless/copyright-header.txt"))
        }
        groovyGradle {
            target("*.gradle", "**/build.gradle")
            // The second argument is the delimiter (regex) where the header should stop
            licenseHeaderFile(
                rootProject.file("config/spotless/copyright-header.txt"),
                "(plugins|import|rootProject)"
            )
        }
    }

    tasks.register<Copy>("installLocalGitHooks") {
        from(File(rootProject.rootDir, ".githooks"))
        into(File(rootProject.rootDir, ".git/hooks"))

        eachFile {
            permissions {
                unix("rwxr-xr-x") // Equivalent to 755
            }
        }
    }

    tasks.build {
        dependsOn("installLocalGitHooks")
    }
}
