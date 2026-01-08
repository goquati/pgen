package de.quati.pgen.shared

private val PG_IDENTIFIER_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")

public fun requireValidPgIdentifier(
    value: String,
    what: String
): Unit = require(PG_IDENTIFIER_REGEX.matches(value)) {
    "Invalid PostgreSQL $what name: '$value'"
}
