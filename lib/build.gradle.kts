import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.publish)
}

repositories {
    mavenCentral()
}

val githubUser = "goquati"
val githubProject = "pgen"
val groupStr = "de.quati.pgen"
val versionStr = System.getenv("GIT_TAG_VERSION") ?: "1.0-SNAPSHOT"

enum class SubProjects(val projectName: String) {
    CORE("core"),
    JDBC("jdbc"),
    R2DBC("r2dbc"),
    SHARED("shared"),
}

tasks.matching { it.name.startsWith("publish") }.configureEach {
    enabled = false // disable for the root project
}

subprojects {
    val projectType = SubProjects.values().singleOrNull { it.projectName == name }
        ?: throw NotImplementedError("no description defined for $name")

    apply(plugin = "com.vanniktech.maven.publish")

    repositories {
        mavenCentral()
    }
    group = groupStr
    version = versionStr

    val artifactId = project.name
    val descriptionStr = when (projectType) {
        SubProjects.CORE -> "Core utilities used by all generated code: column/value helpers, constraint handling, " +
                "shared abstractions"

        SubProjects.SHARED ->
            "Runtime code with no Exposed dependency, containing common models and utilities used by both JDBC and " +
                    "R2DBC output."

        SubProjects.JDBC -> "JDBC-specific helpers and extensions for generated code targeting Exposed JDBC."
        SubProjects.R2DBC ->
            "R2DBC-specific helpers and extensions for non-blocking database access with Exposed R2DBC."
    }
    mavenPublishing {
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
