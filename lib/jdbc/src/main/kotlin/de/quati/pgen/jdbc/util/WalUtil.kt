package de.quati.pgen.jdbc.util

import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

public fun JdbcTransaction.emitLogicalPgMessage(
    transactional: Boolean,
    prefix: String,
    message: String,
    flush: Boolean = false,
): String = exec(
    stmt = "select pg_logical_emit_message(?, ?, ?, ?)",
    args = listOf(
        BooleanColumnType() to transactional,
        TextColumnType() to prefix,
        TextColumnType() to message,
        BooleanColumnType() to flush,
    ),
) {
    it.next()
    it.getString(1)
} ?: error("no result")
