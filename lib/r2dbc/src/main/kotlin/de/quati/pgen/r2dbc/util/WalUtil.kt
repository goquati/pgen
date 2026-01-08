package de.quati.pgen.r2dbc.util

import kotlinx.coroutines.flow.single
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction

public suspend fun R2dbcTransaction.emitLogicalPgMessage(
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
    it.get(0) as String
}?.single() ?: error("no result")
