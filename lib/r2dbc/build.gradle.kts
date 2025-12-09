import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    api(project(":shared"))
    implementation(libs.goquati.base)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.r2dbc)
    implementation(libs.bundles.kotlinx.serialization)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        allWarningsAsErrors = true
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}