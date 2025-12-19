plugins {
    java
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

configurations.all {
    resolutionStrategy {
        force("com.netflix.nebula:redline-rpm:1.2.2")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.2.20")
    implementation(
        "org.jetbrains.kotlin.kapt:org.jetbrains.kotlin.kapt.gradle.plugin:2.2.20"
    )
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.11.0")

    // GraalVM auto reflection config generation.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.formkiq:graalvm-annotations:1.0.0")

    // Packaging
    implementation("com.netflix.nebula:gradle-ospackage-plugin:12.1.1")

    // Gradle API - gives you access to Project, Task, etc.
    implementation(gradleApi())

    // Gradle Kotlin DSL API - for kotlin-dsl features
    implementation(gradleKotlinDsl())

    // Python runner for code translation
    implementation(
        "com.pswidersk.python-plugin:com.pswidersk.python-plugin.gradle" +
                ".plugin:2.9.0"
    )
}