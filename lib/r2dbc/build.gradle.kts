dependencies {
    api(project(":core"))
    api(project(":shared"))
    implementation(project(":intern"))
    implementation(libs.goquati.base)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.r2dbc)
    implementation(libs.bundles.kotlinx.serialization)
}