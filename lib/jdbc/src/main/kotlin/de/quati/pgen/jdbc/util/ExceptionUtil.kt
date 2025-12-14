package de.quati.pgen.jdbc.util

import de.quati.pgen.shared.PgenErrorDetails
import de.quati.pgen.shared.PgenException
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage

public fun ServerErrorMessage.toPgenErrorDetails(): PgenErrorDetails? {
    return PgenErrorDetails(
        code = sqlState ?: return null,
        columnName = column,
        constraintName = constraint,
        dataTypeName = datatype,
        detail = detail,
        file = file,
        hint = hint,
        internalPosition = internalPosition,
        internalQuery = internalQuery,
        line = line,
        message = message ?: "unknown error",
        position = position,
        routine = routine,
        schemaName = schema,
        severityLocalized = severity,
        severityNonLocalized = severity,
        tableName = table,
        where = where,
    )
}

internal fun Throwable.toPgenError(): PgenException = when (this) {
    is ExposedSQLException -> when (val e = cause) {
        is PSQLException -> e.serverErrorMessage?.toPgenErrorDetails()?.let {
            PgenException.of(it)
        } ?: PgenException.Other(msg = message ?: "")

        else -> PgenException.Other(msg = message ?: "")
    }

    else -> PgenException.Other(msg = message ?: "")
}.apply { addSuppressed(this@toPgenError) }
