package de.quati.pgen.core.util

import de.quati.pgen.core.Constraint
import de.quati.pgen.shared.PgenException
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.failureOrNull
import de.quati.kotlin.util.QuatiException
import de.quati.kotlin.util.getOr
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi

@OptIn(InternalApi::class)
private fun Table.matches(error: PgenException.Sql): Boolean = when {
    error.details.schemaName != schemaName -> false
    error.details.tableName != tableNameWithoutScheme -> false
    else -> true
}

public fun Column<out Any>.matches(error: PgenException.Sql): Boolean =
    table.matches(error) && error.details.columnName == name

public fun Constraint.matches(error: PgenException.Sql): Boolean = when (this) {
    is Constraint.NotNull -> column.matches(error)
    is Constraint.Check, is Constraint.ForeignKey,
    is Constraint.PrimaryKey, is Constraint.Unique -> table.matches(error) && error.details.constraintName == name
}

public inline fun <T> Result<T, PgenException>.onIntegrityConstraintViolation(
    block: (PgenException.IntegrityConstraintViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.IntegrityConstraintViolation)?.also { block(it) }
}

public inline fun <T> Result<T, PgenException>.onRestrictViolation(
    block: (PgenException.RestrictViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.RestrictViolation)?.also { block(it) }
}

public inline fun <T, C : Any> Result<T, PgenException>.onNotNullViolation(
    column: Column<C>? = null,
    block: (PgenException.NotNullViolation) -> Unit
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.NotNullViolation)?.also { error ->
        if (column?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onNotNullViolation(
    constraint: Constraint.NotNull? = null,
    block: (PgenException.NotNullViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.NotNullViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onForeignKeyViolation(
    constraint: Constraint.ForeignKey? = null,
    block: (PgenException.ForeignKeyViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.ForeignKeyViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onUniqueViolation(
    constraint: Constraint.IUnique? = null,
    block: (PgenException.UniqueViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.UniqueViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onCheckViolation(
    constraint: Constraint.Check? = null,
    block: (PgenException.CheckViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.CheckViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onSqlViolation(
    constraint: Constraint? = null,
    block: (PgenException.Sql) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.Sql)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

public inline fun <T> Result<T, PgenException>.onExclusionViolation(
    block: (PgenException.ExclusionViolation) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.ExclusionViolation)?.also { block(it) }
}

public inline fun <T> Result<T, PgenException>.onNoneSqlException(
    block: (PgenException.Other) -> Unit,
): Result<T, PgenException> = apply {
    (failureOrNull as? PgenException.Other)?.also { block(it) }
}

public fun <T> Result<T, PgenException>.getOrThrowInternalServerError(msg: String): T = getOr {
    throw QuatiException.InternalServerError(msg, t = it)
}

private val PgenException.Sql.prettyMsg
    get(): String {
        val name = when (this) {
            is PgenException.CheckViolation -> "check violation"
            is PgenException.ExclusionViolation -> "exclusion violation"
            is PgenException.ForeignKeyViolation -> "foreign key violation"
            is PgenException.Generic -> "unknown error"
            is PgenException.IntegrityConstraintViolation -> "integrity constraint violation"
            is PgenException.NotNullViolation -> "not null violation"
            is PgenException.RestrictViolation -> "restrict violation"
            is PgenException.UniqueViolation -> "unique violation"
        }
        val col = listOfNotNull(
            details.schemaName,
            details.tableName,
            details.columnName,
        ).takeIf { it.isNotEmpty() }
            ?.joinToString(".")
            ?.let { "on $it" }
        val constraint = details.constraintName?.let { "($it)" }
        return listOfNotNull(name, col, constraint).joinToString(" ")
    }

public fun PgenException.toQuatiException(): QuatiException = when (this) {
    is PgenException.Other -> QuatiException.InternalServerError(msg)
    is PgenException.Generic -> QuatiException.InternalServerError("$msg: $prettyMsg", this)

    is PgenException.NotNullViolation, is PgenException.IntegrityConstraintViolation,
    is PgenException.CheckViolation -> QuatiException.BadRequest("$msg: $prettyMsg", this)

    is PgenException.ExclusionViolation, is PgenException.ForeignKeyViolation,
    is PgenException.RestrictViolation, is PgenException.UniqueViolation ->
        QuatiException.Conflict("$msg: $prettyMsg", this)
}
