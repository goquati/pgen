import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

val groupStr = "de.quati.pgen"
val gitRepo = "https://github.com/goquati/pgen"

version = System.getenv("GIT_TAG_VERSION") ?: "1.0.0-SNAPSHOT"
group = groupStr

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.poet)
    implementation(libs.goquati.base)
    implementation(libs.goquati.poet)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.flyway)
    implementation(libs.bundles.jackson)
    compileOnly(kotlin("gradle-plugin"))
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

gradlePlugin {
    website = gitRepo
    vcsUrl = "$gitRepo.git"

    val pgen by plugins.creating {
        id = groupStr
        implementationClass = "$groupStr.plugin.PgenPlugin"
        displayName = "Generate Kotlin Exposed tables from a PostgreSQL database schema"
        description = """
           |This Gradle plugin simplifies the development process by automatically generating Kotlin Exposed table 
           |definitions from a PostgreSQL database schema. It connects to your database, introspects the schema, and 
           |creates Kotlin code for Exposed DSL, including table definitions, column mappings, and relationships. Save 
           |time and eliminate boilerplate by keeping your Exposed models synchronized with your database schema 
           |effortlessly.""".trimMargin().trim()
        tags =
            listOf("Kotlin Exposed", "PostgreSQL", "Exposed", "Kotlin DSL", "Database Integration", "Code Generation")
    }
}
