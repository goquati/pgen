plugins {
    alias(libs.plugins.kotlinJvm)
}

group = "de.quati.pgen"
version = System.getenv("GIT_TAG_VERSION") ?: "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}