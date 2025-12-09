plugins {
    kotlin("jvm") version "2.2.0"
}

group = "de.quati.pgen"

repositories {
    mavenCentral()
}

dependencies {
    val testContainersVersion = "1.21.3"
    implementation("org.testcontainers:testcontainers:$testContainersVersion")
    implementation("org.testcontainers:postgresql:$testContainersVersion")
    implementation("org.postgresql:postgresql:42.7.7")
}
