import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.2.0"
    id("de.quati.pgen") version "0.39.0"
}

group = "de.quati.pgen.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val exposedVersion = "1.0.0-rc-4"
    implementation("de.quati.pgen:r2dbc:0.39.0")
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-crypt:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-json:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-r2dbc:${exposedVersion}")
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")

    implementation("de.quati:kotlin-util:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

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

tasks.register<JavaExec>("runMain") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("de.quati.pgen.example.r2dbc.MainKt")
}
tasks.compileKotlin {
    dependsOn("pgenFlywayMigration")
    dependsOn("pgenGenerateCode")
}

pgen {
    addDb("base") {
        connectionConfig {
            url("jdbc:postgresql://localhost:55420/postgres")
            user("postgres")
            password("postgres")
        }
        flyway { // optional
            migrationDirectory("$projectDir/src/main/resources/db/migration")
        }
        tableFilter {
            addSchemas("public")
        }
        typeMappings {
            add("public.user_id", clazz = "$group.r2dbc.UserId")
        }
    }

    packageName("$group.r2dbc.generated")
    outputPath("$projectDir/src/main/kotlin/de/quati/pgen/example/r2dbc/generated")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    setConnectionTypeR2dbc()
}