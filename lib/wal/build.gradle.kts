plugins {
    alias(libs.plugins.serialization)
}

dependencies {
    api(project(":core"))
    api(project(":shared"))
    implementation(libs.goquati.base)
    implementation(libs.jdbc.postgresql)
    implementation(libs.slf4j)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.kotlinx.serialization)
}