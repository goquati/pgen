package de.quati.pgen.build

interface DbTestcontainerConfig {
    val port: Int
    val type: Type get() = Type.Pg17
    val containerName get() = "pgen-dev-db-$port"
    val jdbcUrl get() = "jdbc:postgresql://localhost:$port/postgres"

    enum class Type(internal val image: String) {
        Pg17("postgres:17.2"),
        PgVector("pgvector/pgvector:0.8.0-pg17"),
    }
}
