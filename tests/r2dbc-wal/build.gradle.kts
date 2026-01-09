import buildkit.EnvFile
import buildkit.registerStartDbTask

dependencies {
    implementation(project(":sharedTest"))
    implementation("de.quati.pgen:r2dbc:1.0.0-SNAPSHOT")
    implementation("de.quati.pgen:wal:1.0.0-SNAPSHOT")
    implementation(libs.jdbc.postgresql)
    implementation(libs.goquati.base)
    implementation(libs.ipaddress)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.r2dbc)
}

val envFile = EnvFile(rootProject)
val dbPort = envFile.getDbPort("r2dbc_wal")

registerStartDbTask(profile = "r2dbc-wal")
tasks.findByName("pgenFlywayMigration")!!.dependsOn("startDb")
tasks.findByName("pgenGenerate")!!.dependsOn("pgenFlywayMigration")
tasks.findByName("check")!!.apply {
    dependsOn("pgenGenerate")
    finalizedBy("stopDb")
}
tasks.compileKotlin { dependsOn("pgenGenerateCode") }

pgen {
    val baseModule = "${group}.tests.r2dbc.wal"
    val sharedModule = "$baseModule.shared"
    val outputModule = "$baseModule.generated"
    addDb("base") {
        connection {
            url("jdbc:postgresql://localhost:$dbPort/postgres")
            user("postgres")
            password("postgres")
        }
        flyway { // optional
            migrationDirectory("$projectDir/src/main/resources/migration")
        }
        tableFilter {
            addSchemas("public")
        }
        typeMappings {
            add("public.user_id", clazz = "$sharedModule.UserId")
            add("public.email", clazz = "$sharedModule.Email")
        }
        enumMappings {
            add(sqlType = "public.role", clazz = "$sharedModule.RoleDto")
        }
    }

    packageName(outputModule)
    outputPath("$projectDir/src/main/kotlin/${outputModule.replace('.', '/')}")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    setConnectionTypeR2dbc()
}