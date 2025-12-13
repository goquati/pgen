import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    id("de.quati.pgen") version "1.0.0-SNAPSHOT"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "de.quati.pgen")

    repositories {
        mavenCentral()
    }
    group = "de.quati.pgen"
    version = System.getenv("GIT_TAG_VERSION") ?: "1.0-SNAPSHOT"

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            allWarningsAsErrors = true
            jvmTarget.set(JvmTarget.JVM_21)
            languageVersion.set(KotlinVersion.KOTLIN_2_2)
            freeCompilerArgs.add("-Xcontext-parameters")
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }

    dependencies {
        testImplementation(kotlin("test"))
        testImplementation("io.kotest:kotest-assertions-core:6.0.7")
    }

    tasks.test {
        useJUnitPlatform()
        maxParallelForks = 1
    }
}