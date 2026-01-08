package de.quati.pgen.tests.jdbc.basic

import de.quati.pgen.tests.shared.EnvFile
import org.jetbrains.exposed.v1.jdbc.Database

fun createDb(name: String): Database {
    val port = EnvFile.getDbPort(name)
    return Database.connect(
        "jdbc:postgresql://localhost:$port/postgres",
        user = "postgres",
        password = "postgres",
    )
}
