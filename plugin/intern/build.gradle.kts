import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.poet)
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.flyway)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
