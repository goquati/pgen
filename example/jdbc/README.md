# JDBC Example

## Prerequisites

- Docker (for the convenience database)
- JDK 21+

## Run it

1. Start Postgres:
    - `docker-compose up -d`
2. Execute the example:
    - `./gradlew runMain`

You should see output similar to:

```
All users in the database:
  • username=NonEmptyText(value=admin-...), id=...uuid...
```

## Gradle plugin configuration

The pgen Gradle plugin is configured in [build.gradle.kts](./build.gradle.kts) of this example:

```
plugins {
    kotlin("jvm") version "2.2.0"
    id("de.quati.pgen") version "0.41.0"
}

pgen {
    // database, flyway, filters, type mappings, and output settings
}
```

The `pgen { ... }` block defines the database connection, optional Flyway migration directory, table filters, custom
type mappings, and where the generated sources are written. See [build.gradle.kts](./build.gradle.kts) for the full
configuration used by this example.
