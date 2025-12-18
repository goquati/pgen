import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.publish)
}

repositories {
    mavenCentral()
}

val githubUser = "goquati"
val githubProject = "pgen"

enum class SubProjects(val projectName: String) {
    CORE("core"),
    JDBC("jdbc"),
    R2DBC("r2dbc"),
    SHARED("shared"),
    INTERN("intern"),
}

tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false // disable for the root project
}

subprojects {
    val projectType = SubProjects.values().singleOrNull { it.projectName == name }
        ?: throw NotImplementedError("no description defined for $name")

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "com.vanniktech.maven.publish")

    repositories {
        mavenCentral()
    }
    group = "de.quati.pgen"
    version = System.getenv("GIT_TAG_VERSION") ?: "1.0-SNAPSHOT"

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

    val artifactId = project.name

    if (projectType in setOf(SubProjects.INTERN)) return@subprojects
    mavenPublishing {
        val descriptionStr = when (projectType) {
            SubProjects.CORE -> "Core utilities used by all generated code: column/value helpers, constraint " +
                    "handling, shared abstractions"

            SubProjects.SHARED ->
                "Runtime code with no Exposed dependency, containing common models and utilities used by both JDBC " +
                        "and R2DBC output."

            SubProjects.JDBC -> "JDBC-specific helpers and extensions for generated code targeting Exposed JDBC."
            SubProjects.R2DBC ->
                "R2DBC-specific helpers and extensions for non-blocking database access with Exposed R2DBC."

            SubProjects.INTERN -> error("should not be published")
        }
        coordinates(
            groupId = project.group as String,
            artifactId = artifactId,
            version = project.version as String
        )
        pom {
            name = artifactId
            description = descriptionStr
            url = "https://github.com/$githubUser/$githubProject"
            licenses {
                license {
                    name = "MIT License"
                    url = "https://github.com/$githubUser/$githubProject/blob/main/LICENSE"
                }
            }
            developers {
                developer {
                    id = githubUser
                    name = githubUser
                    url = "https://github.com/$githubUser"
                }
            }
            scm {
                url = "https://github.com/${githubUser}/${githubProject}"
                connection = "scm:git:https://github.com/${githubUser}/${githubProject}.git"
                developerConnection = "scm:git:git@github.com:${githubUser}/${githubProject}.git"
            }
        }
        publishToMavenCentral(
            SonatypeHost.CENTRAL_PORTAL,
            automaticRelease = true,
        )
        signAllPublications()
    }
}
