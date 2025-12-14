package de.quati.pgen.r2dbc.util

import de.quati.pgen.shared.PgenErrorDetails
import de.quati.pgen.shared.PgenException
import io.r2dbc.postgresql.api.ErrorDetails
import io.r2dbc.postgresql.api.PostgresqlException
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import kotlin.jvm.optionals.getOrNull

public fun ErrorDetails.toPgenErrorDetails(): PgenErrorDetails = PgenErrorDetails(
    code = code,
    columnName = columnName.getOrNull(),
    constraintName = constraintName.getOrNull(),
    dataTypeName = dataTypeName.getOrNull(),
    detail = detail.getOrNull(),
    file = file.getOrNull(),
    hint = hint.getOrNull(),
    internalPosition = internalPosition.getOrNull()?.toIntOrNull(),
    internalQuery = internalQuery.getOrNull(),
    line = line.getOrNull()?.toIntOrNull(),
    message = message,
    position = position.getOrNull()?.toIntOrNull(),
    routine = routine.getOrNull(),
    schemaName = schemaName.getOrNull(),
    severityLocalized = severityLocalized,
    severityNonLocalized = severityNonLocalized,
    tableName = tableName.getOrNull(),
    where = where.getOrNull(),
)

internal fun Throwable.toPgenError(): PgenException = when (this) {
    is ExposedR2dbcException -> when (val e = cause) {
        is PostgresqlException -> PgenException.of(
            details = e.errorDetails.toPgenErrorDetails(),
        )

        else -> PgenException.Other(msg = message)
    }

    else -> PgenException.Other(msg = message ?: "")
}.apply { addSuppressed(this@toPgenError) }
