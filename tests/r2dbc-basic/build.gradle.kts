import de.quati.pgen.build.DbTestcontainerConfig
import de.quati.pgen.build.StartDbTestcontainerTask
import de.quati.pgen.plugin.model.config.Config
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinJvm)
    id("de.quati.pgen") version "1.0.0-SNAPSHOT"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation("de.quati.pgen:r2dbc:1.0.0-SNAPSHOT")
    implementation(libs.goquati.base)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.exposed.r2dbc)
    implementation(libs.bundles.kotlinx.serialization)

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:6.0.7")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
}
kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xcontext-parameters")
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

enum class Db(override val port: Int) : DbTestcontainerConfig {
    FOO(55430),
    BAR(55431) {
        override val type = DbTestcontainerConfig.Type.PgVector
    },
}

tasks.register<StartDbTestcontainerTask>("pgenDevDbR2dbcBasic") { configs.set(Db.values().toList()) }
tasks.findByName("pgenFlywayMigration")!!.dependsOn("pgenDevDbR2dbcBasic")
tasks.findByName("pgenGenerate")!!.dependsOn("pgenFlywayMigration")
tasks.findByName("check")!!.dependsOn("pgenGenerate")
tasks.compileKotlin { dependsOn("pgenGenerateCode") }

pgen {
    val baseModule = "${rootProject.group}.tests.r2dbc.basic"
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
    connectionType(Config.ConnectionType.R2DBC)
}