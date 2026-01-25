import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version "2.2.0"
    id("de.quati.pgen") version "0.42.0"
}

group = "de.quati.pgen.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val exposedVersion = "1.0.0"
    implementation("de.quati.pgen:jdbc:0.42.0")
    implementation("org.jetbrains.exposed:exposed-core:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-crypt:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-dao:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-json:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:${exposedVersion}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${exposedVersion}")
    implementation("org.postgresql:postgresql:42.7.4")

    implementation("de.quati:kotlin-util:2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
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
    mainClass.set("de.quati.pgen.example.jdbc.MainKt")
}
tasks.compileKotlin {
    dependsOn("pgenFlywayMigration")
    dependsOn("pgenGenerateCode")
}

pgen {
    addDb("base") {
        connection {
            url("jdbc:postgresql://localhost:55421/postgres")
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
            add("public.user_id", clazz = "$group.jdbc.UserId")
        }
    }

    packageName("$group.jdbc.generated")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    setConnectionTypeJdbc()
}