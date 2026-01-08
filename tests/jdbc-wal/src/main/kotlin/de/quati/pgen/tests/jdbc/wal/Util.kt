package de.quati.pgen.tests.jdbc.wal

import de.quati.pgen.tests.shared.EnvFile
import org.jetbrains.exposed.v1.jdbc.Database

private val port = EnvFile.getDbPort("jdbc-wal")
val URL = "jdbc:postgresql://localhost:$port/postgres"

fun createDb(): Database = Database.connect(
    URL,
    user = "postgres",
    password = "postgres",
)