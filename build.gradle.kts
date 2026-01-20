plugins {
    id "com.diffplug.spotless" version "6.25.0"
}

spotless {
    java {
        target 'src/**/*.java'
         licenseHeaderFile rootProject.file('LICENSE_HEADER.txt')
    }
    kotlin {
        target 'src/**/*.kt'
        licenseHeaderFile rootProject.file('LICENSE_HEADER.txt')
    }
    groovyGradle {
        target '*.gradle', '**/build.gradle'
         licenseHeaderFile rootProject.file('LICENSE_HEADER.txt'), '(plugins|import|rootProject)'
    }
}