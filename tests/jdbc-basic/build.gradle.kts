import de.quati.pgen.build.DbTestcontainerConfig
import de.quati.pgen.build.StartDbTestcontainerTask
import de.quati.pgen.plugin.model.config.Config

dependencies {
    implementation("de.quati.pgen:jdbc:1.0.0-SNAPSHOT")
    implementation(libs.goquati.base)
    implementation(libs.ipaddress)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.jdbc)
}

enum class Db(override val port: Int) : DbTestcontainerConfig {
    FOO(55432),
    BAR(55433) {
        override val type = DbTestcontainerConfig.Type.PgVector
    },
}

tasks.register<StartDbTestcontainerTask>("pgenDevDbJdbcBasic") { configs.set(Db.values().toList()) }
tasks.findByName("pgenFlywayMigration")!!.dependsOn("pgenDevDbJdbcBasic")
tasks.findByName("pgenGenerate")!!.dependsOn("pgenFlywayMigration")
tasks.findByName("check")!!.dependsOn("pgenGenerate")
tasks.compileKotlin { dependsOn("pgenGenerateCode") }

pgen {
    val baseModule = "${group}.tests.jdbc.basic"
    val sharedModule = "$baseModule.shared"
    val outputModule = "$baseModule.generated"
    addDb("foo") {
        connectionConfig {
            url(Db.FOO.jdbcUrl)
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
        connectionConfig {
            url(Db.BAR.jdbcUrl)
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
    outputPath("$projectDir/src/main/kotlin/${outputModule.replace('.', '/')}")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    connectionType(Config.ConnectionType.JDBC)
}