# pgen – PostgreSQL Schema → Kotlin Exposed/R2DBC Code Generator

Build type-safe Kotlin data access from your PostgreSQL schema with a single Gradle plugin.

pgen connects to a PostgreSQL database, introspects the schema, writes a stable YAML specification, and generates Kotlin
sources for JetBrains Exposed with JDBC or R2DBC. Use it locally against a real DB, then regenerate deterministically
from the checked-in spec in CI — no database required there.

---

## ✨ Highlights

- Schema-first workflow: DB → `pgen-spec.yaml` → Kotlin
- Deterministic CI builds: generate from spec only (no DB)
- JDBC and R2DBC support
- Optional Flyway migrations before extraction
- Custom mappings for Postgres ENUMs, domains, and user-defined types
- Type overwrites for custom Kotlin wrappers/value classes
- Multiple databases per project supported

---

## ✅ Requirements

- JDK 21+
- Gradle 8+
- PostgreSQL

---

## 🚀 Quick Start

### 1) Apply the plugin

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    id("de.quati.pgen") version "0.45.0"
}
```

### 2) Configure pgen

Minimal but complete example with one database, Flyway, custom mappings, and R2DBC codegen:

```kotlin
pgen {
    val baseModule = "${rootProject.group}.example"
    val sharedModule = "$baseModule.shared"
    val outputModule = "$baseModule.generated"

    addDb("base") {
        connection {
            url("jdbc:postgresql://localhost:53333/postgres")
            user("postgres")
            password("postgres")
        }
        flyway { migrationDirectory("$projectDir/src/main/resources/migration/base") }
        tableFilter { addSchemas("public") }
        typeMappings {
            add("public.user_id", clazz = "$sharedModule.UserId")
            add("public.email", clazz = "$sharedModule.Email")
        }
        enumMappings { add(sqlType = "public.role", clazz = "$sharedModule.RoleDto") }
        typeOverwrites {
            add("public.foo.id", clazz = "$sharedModule.MyId", parseFunction = "foo")
            add("public.hello.id", clazz = "$sharedModule.HelloId")
        }
    }

    packageName(outputModule)
    outputPath("$projectDir/src/main/kotlin/${outputModule.replace('.', '/')}")
    specFilePath("$projectDir/src/main/resources/pgen-spec.yaml")
    setConnectionTypeR2dbc() // or setConnectionTypeJdbc()
}
```

### 3) Make Kotlin compilation depend on code generation

```kotlin
tasks.compileKotlin { dependsOn("pgenGenerateCode") }
```

### 4) Local workflow

```bash
./gradlew pgenFlywayMigration   # optional, when Flyway is configured
./gradlew pgenGenerateSpec      # connect to DB, produce pgen-spec.yaml
./gradlew pgenGenerateCode      # generate Kotlin from spec
```

Commit only the spec, since the generated code is fully reproducible from it.

### CI workflow (no DB required)

```bash
./gradlew pgenGenerateCode
```

---

## 🧩 Gradle tasks

| Task                  | Description                                                                   |
|-----------------------|-------------------------------------------------------------------------------|
| `pgenFlywayMigration` | Runs Flyway migrations (if configured).                                       |
| `pgenGenerateSpec`    | Connects to PostgreSQL, introspects schema, and generates the YAML spec file. |
| `pgenGenerateCode`    | Generates Kotlin code from the spec only. No DB required.                     |
| `pgenGenerate`        | Shorthand: `pgenGenerateSpec` + `pgenGenerateCode`.                           |

---

## 📂 Project structure (example layout)

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

## ⚙️ Configuration overview

Key configuration blocks inside `pgen { … }`:

- `addDb("name") { … }`
    - `connectionConfig { url(..); user(..); password(..) }`
    - `flyway { migrationDirectory("…") }` (optional)
    - `tableFilter { addSchemas("public"), include/exclude tables }`
    - `typeMappings { add("schema.domain", clazz = "com.example.Email") }`
    - `enumMappings { add(sqlType = "schema.enum", clazz = "com.example.Role") }`
    - `typeOverwrites { add("schema.table.column", clazz = "…", parseFunction = "…") }`
- Top-level outputs
    - `packageName("…")`, `outputPath("…")`, `specFilePath("…")`
    - `connectionType(Config.ConnectionType.R2DBC | JDBC)`

See the example modules ([R2DBC](example/r2dbc/build.gradle.kts) and [JDBC](example/jdbc/build.gradle.kts)) for full, working configurations.

---

## 🧪 Examples

- JDBC example: [example/jdbc](example/jdbc)
    - How to run: see [example/jdbc/README.md](example/jdbc/README.md)
- R2DBC example: [example/r2dbc](example/r2dbc)
    - How to run: see [example/r2dbc/README.md](example/r2dbc/README.md)

Both examples use the plugin to generate sources, then run a tiny program printing rows from the DB.

---

## 📘 What gets generated?

The generator produces:

- Exposed `Table` objects (`Users`, `Orders`, …)
- `Entity` data classes
- `CreateEntity` and `UpdateEntity` models for inserts/updates
- `Enum`, `Composite` and `Domain` types
- Constraint objects to detect DB errors
- Helper extensions such as `deleteSingle`, `updateSingle`, etc.

---

## 🛠 Dependencies (reference)

Below is a reference set you can adapt to your project:

```kotlin
dependencies {
    implementation("de.quati.pgen:r2dbc:0.45.0")

    // Exposed core modules
    val exposedVersion = "1.0.0-rc-4"
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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("de.quati:kotlin-util:2.0.0")
}
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
