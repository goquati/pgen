plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "lib"

include(":shared")
include(":core")
include(":jdbc")
include(":r2dbc")
include(":wal")
