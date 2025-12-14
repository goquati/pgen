package de.quati.pgen.tests.jdbc.basic

import org.jetbrains.exposed.v1.jdbc.Database

fun createDb(port: Int) = Database.connect(
    "jdbc:postgresql://localhost:$port/postgres",
    user = "postgres",
    password = "postgres",
)
