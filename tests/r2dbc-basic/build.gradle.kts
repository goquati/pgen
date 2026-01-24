import buildkit.EnvFile
import buildkit.registerStartDbTask

plugins {
    id("de.quati.pgen") version "1.0.0-SNAPSHOT"
}

dependencies {
    implementation(project(":sharedTest"))
    implementation("de.quati.pgen:r2dbc:1.0.0-SNAPSHOT")
    implementation(libs.goquati.base)
    implementation(libs.ipaddress)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.r2dbc)
}

val envFile = EnvFile(rootProject)
val dbPortBase = envFile.getDbPort("r2dbc_basic")
val dbPortBaseVector = envFile.getDbPort("r2dbc_basic_vector")

registerStartDbTask(profile = "r2dbc-base")
tasks.findByName("pgenFlywayMigration")!!.dependsOn("startDb")
tasks.findByName("pgenGenerate")!!.dependsOn("pgenFlywayMigration")
tasks.findByName("check")!!.apply {
    dependsOn("pgenGenerate")
    finalizedBy("stopDb")
}
tasks.compileKotlin { dependsOn("pgenGenerateCode") }

pgen {
    val baseModule = "${group}.tests.r2dbc.basic"
    val sharedModule = "$baseModule.shared"
    val outputModule = "$baseModule.generated"
    addDb("foo") {
        connection {
            url("jdbc:postgresql://localhost:$dbPortBase/postgres")
            user("postgres")
            password("postgres")
        }
        flyway { // optional
            migrationDirectory("$projectDir/src/main/resources/migration/foo")
        }
        tableFilter {
            addSchemas("public")
        }
        statements {
            //addScript("./test-queries.sql")
        }
        typeMappings {
            add("public.user_id", clazz = "$sharedModule.UserId")
            add("public.email", clazz = "$sharedModule.Email")
        }
        enumMappings {
            add(sqlType = "public.role", clazz = "$sharedModule.RoleDto")
        }
        typeOverwrites {
            add("public.foo.id", clazz = "$sharedModule.MyId", parseFunction = "foo")
            add("public.hello.id", clazz = "$sharedModule.HelloId")
        }
        columnTypeMappings {
            add(
                sqlType = "pg_catalog.inet",
                columnTypeClass = "$sharedModule.InetColumnType",
                valueClass = "inet.ipaddr.IPAddress",
            )
        }
    }
    addDb("bar") {
        connection {
            url("jdbc:postgresql://localhost:$dbPortBaseVector/postgres")
            user("postgres")
            password("postgres")
        }
        flyway { // optional
            migrationDirectory("$projectDir/src/main/resources/migration/bar")
        }
        tableFilter {
            addSchemas("public")
        }
        typeMappings {
            add("stripe.account_id", clazz = "$sharedModule.StripeId.Account", parseFunction = "parse")
            add("stripe.customer_id", clazz = "$sharedModule.StripeId.Customer")
        }
        typeOverwrites {
            add("keycloak.realm.id", clazz = "$sharedModule.KeycloakId.Realm")
        }
    }

    packageName(outputModule)
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    setConnectionTypeR2dbc()
}