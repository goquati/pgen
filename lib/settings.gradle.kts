plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "lib"

include(":shared")
include(":core")
include(":jdbc")
include(":r2dbc")
