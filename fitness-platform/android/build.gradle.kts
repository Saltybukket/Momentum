plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("**/*.md", "**/*.xml", "**/*.properties", "**/*.toml")
        targetExclude("**/build/**", "gradle/wrapper/gradle-wrapper.properties")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    source.setFrom(
        fileTree(rootDir) {
            include(
                "app/src/**/*.kt",
                "core/**/src/**/*.kt",
                "domain/src/**/*.kt",
                "data/src/**/*.kt",
                "feature/**/src/**/*.kt",
            )
            exclude("**/build/**", "**/generated/**")
        },
    )
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    parallel = true
    autoCorrect = false
}

subprojects {
    plugins.withId("com.android.library") {
        tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
            onlyIf { file("src/androidTest").isDirectory }
        }
    }
}

tasks.register("connectedProjectAndroidTest") {
    group = "verification"
    description = "Runs only Android modules that currently contain instrumentation tests."
    dependsOn(
        ":core:database:connectedDebugAndroidTest",
        ":data:connectedDebugAndroidTest",
        ":app:connectedDebugAndroidTest",
    )
}
