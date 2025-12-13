# pgen – PostgreSQL Schema → Exposed/R2DBC Code Generator

*A Gradle plugin for type-safe, schema-first Kotlin database access*

pgen is a Gradle plugin that connects to a **PostgreSQL database**, introspects its schema, and generates:

* A **specification file** (`pgen-spec.yaml`) describing the DB schema and mappings
* Kotlin **Exposed table definitions**, **entity DTOs**, **update/create models**
* **JDBC** and **R2DBC** support
* Optional **Flyway migrations**
* Mappings for **Postgres ENUMs**, **domains**, and **custom types**
* Type overwrites for custom Kotlin wrappers

The generated code enables fully type-safe access to your database without manually writing Exposed table definitions.

---

## ✨ Features

### ✔ Schema-first: DB → YAML → Kotlin

Run the plugin locally against a database to produce a stable YAML “spec” file.
Commit this file to version control.

### ✔ Code generation without a running DB (CI-friendly)

In CI you do **not** need a PostgreSQL database.
Just run:

```bash
./gradlew pgenGenerateCode
```

This reads the checked-in YAML spec file and regenerates Kotlin sources.

### ✔ Custom type & enum mappings

Map PostgreSQL types and domains to custom Kotlin value classes.

### ✔ Flyway integration (optional)

Automatically run migrations before schema extraction.

### ✔ Multiple databases

Generate separate modules for multiple DBs.

---

## 🚀 Quick Start

### 1. Apply the plugin

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    id("de.quati.pgen") version "0.34.0"
}
```

### 2. Configure pgen

Below is a complete example with a single database, custom mappings, and codegen setup.

```kotlin
pgen {
    val baseModule = "${rootProject.group}.example"
    val sharedModule = "$baseModule.shared"
    val outputModule = "$baseModule.generated"

    addDb("base") {
        connectionConfig {
            url("jdbc:postgresql://localhost:53333/postgres")
            user("postgres")
            password("postgres")
        }
        flyway {
            migrationDirectory("$projectDir/src/main/resources/migration/base")
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
        typeOverwrites {
            add("public.foo.id", clazz = "$sharedModule.MyId", parseFunction = "foo")
            add("public.hello.id", clazz = "$sharedModule.HelloId")
        }
    }

    packageName(outputModule)
    outputPath("$projectDir/src/main/kotlin/${outputModule.replace('.', '/')}")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    connectionType(Config.ConnectionType.R2DBC)
}
```

### 3. Ensure Kotlin compilation depends on code generation

```kotlin
tasks.compileKotlin { dependsOn("pgenGenerateCode") }
```

---

# 🧩 Gradle Tasks

pgen provides the following tasks:

| Task                  | Description                                                                   |
| --------------------- | ----------------------------------------------------------------------------- |
| `pgenFlywayMigration` | Runs Flyway migrations (if configured).                                       |
| `pgenGenerateSpec`    | Connects to PostgreSQL, introspects schema, and generates the YAML spec file. |
| `pgenGenerateCode`    | Generates Kotlin code **only from the spec file**. No DB required.            |
| `pgenGenerate`        | Runs `pgenGenerateSpec` + `pgenGenerateCode`.                                 |

## Workflow Summary

### 🖥 Local development

1. Have a running PostgreSQL database.
2. Make schema changes.
3. Run:

```bash
./gradlew pgenGenerateSpec
./gradlew pgenGenerateCode
```

4. Commit the generated `pgen-spec.yaml` and the generated Kotlin code.

### 🤖 CI pipeline

CI **does not need a running DB**.

Just run:

```bash
./gradlew pgenGenerateCode
```

This guarantees deterministic regeneration based on the committed spec.

---

# 📂 Project Structure

Example layout:

```
src/
  main/
    kotlin/
      de/quati/pgen/example/generated/   ← generated sources
    resources/
      pgen-spec.yaml                     ← generated schema spec
      migration/base/                    ← Flyway migrations
```

---

# 🛠 Dependencies & Versions

Your project should declare dependencies for Exposed, pgen, Kotlin, R2DBC, etc.:

```kotlin

dependencies {
    val exposedVersion = "1.0.0-rc-4"
    val coroutineVersion = "1.10.2"
    val serializationVersion = "1.9.0"

    implementation("de.quati.pgen:r2dbc:0.34.0")
    implementation("de.quati:kotlin-util:2.0.0")

    // Exposed core modules
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    // Exposed JDBC + PostgreSQL
    // implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    // implementation("org.postgresql:postgresql:42.7.4")

    // Exposed R2DBC + drivers
    implementation("org.jetbrains.exposed:exposed-r2dbc:$exposedVersion")
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
    implementation("io.r2dbc:r2dbc-pool:1.0.2.RELEASE")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutineVersion")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$serializationVersion")
}
```

---

# 📘 Example: Using Generated Code

The generated Kotlin structures follow this pattern:

* `Table` objects (`Users`, `Orders`, …)
* `Entity` data classes
* `CreateEntity` and `UpdateEntity` for insert/update
* Constraint objects to detect DB errors
* Extension helpers like `deleteSingle`, `updateSingle`, etc.

---

# 📄 License

This project is licensed under the [MIT License](LICENSE).
