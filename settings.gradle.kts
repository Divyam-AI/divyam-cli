plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include("cli")
include("client")
rootProject.name = "divyam"

// Apply the prefix to all subproject names
rootProject.children.forEach { subproject ->
    subproject.name = "${rootProject.name}-${subproject.name}"
}