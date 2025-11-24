package default_code.util

import io.github.goquati.kotlin.util.Result
import io.github.goquati.kotlin.util.failureOrNull
import io.github.goquati.kotlin.util.QuatiException
import io.github.goquati.kotlin.util.getOr
import default_code.column_type.Constraint
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi

data class PgenErrorDetails(
    val code: String,
    val columnName: String?,
    val constraintName: String?,
    val dataTypeName: String?,
    val detail: String?,
    val file: String?,
    val hint: String?,
    val internalPosition: String?,
    val internalQuery: String?,
    val line: String?,
    val message: String,
    val position: String?,
    val routine: String?,
    val schemaName: String?,
    val severityLocalized: String,
    val severityNonLocalized: String,
    val tableName: String?,
    val where: String?,
)

sealed class PgenException(
    val msg: String,
    throwable: Throwable? = null,
) : RuntimeException(msg, throwable) {
    companion object Companion {
        internal fun of(details: PgenErrorDetails, t: Throwable? = null): PgenException {
            val code = details.code.uppercase()
            val msg = details.message
            return when (code) {
                "23000" -> IntegrityConstraintViolation(details = details, msg = msg, t = t)
                "23001" -> RestrictViolation(details = details, msg = msg, t = t)
                "23502" -> NotNullViolation(details = details, msg = msg, t = t)
                "23503" -> ForeignKeyViolation(details = details, msg = msg, t = t)
                "23505" -> UniqueViolation(details = details, msg = msg, t = t)
                "23514" -> CheckViolation(details = details, msg = msg, t = t)
                "23P01" -> ExclusionViolation(details = details, msg = msg, t = t)
                else -> Generic(details = details, msg = msg, t = t)
            }
        }
    }

    sealed class Sql(
        val details: PgenErrorDetails,
        msg: String,
        throwable: Throwable? = null,
    ) : PgenException(msg, throwable)

    class IntegrityConstraintViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) :
        Sql(details, msg, t)

    class RestrictViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class NotNullViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class ForeignKeyViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class UniqueViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class CheckViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class ExclusionViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class Generic(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    class Other(msg: String, t: Throwable? = null) : PgenException(msg, t)
}

private fun Table.matches(error: PgenException.Sql): Boolean {
    if (error.details.schemaName != schemaName) return false
    @OptIn(InternalApi::class)
    if (error.details.tableName != tableNameWithoutScheme) return false
    return true
}

fun Column<out Any>.matches(error: PgenException.Sql): Boolean =
    table.matches(error) && error.details.columnName == name

fun Constraint.matches(error: PgenException.Sql): Boolean = when (this) {
    is Constraint.NotNull -> column.matches(error)
    is Constraint.Check, is Constraint.ForeignKey,
    is Constraint.PrimaryKey, is Constraint.Unique -> table.matches(error) && error.details.constraintName == name
}

inline fun <T> Result<T, PgenException>.onIntegrityConstraintViolation(block: (PgenException.IntegrityConstraintViolation) -> Unit) =
    apply { (failureOrNull as? PgenException.IntegrityConstraintViolation)?.also { block(it) } }

inline fun <T> Result<T, PgenException>.onRestrictViolation(block: (PgenException.RestrictViolation) -> Unit) =
    apply { (failureOrNull as? PgenException.RestrictViolation)?.also { block(it) } }

inline fun <T, C : Any> Result<T, PgenException>.onNotNullViolation(
    column: Column<C>? = null,
    block: (PgenException.NotNullViolation) -> Unit
) = apply {
    (failureOrNull as? PgenException.NotNullViolation)?.also { error ->
        if (column?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onNotNullViolation(
    constraint: Constraint.NotNull? = null,
    block: (PgenException.NotNullViolation) -> Unit,
) = apply {
    (failureOrNull as? PgenException.NotNullViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onForeignKeyViolation(
    constraint: Constraint.ForeignKey? = null,
    block: (PgenException.ForeignKeyViolation) -> Unit,
) = apply {
    (failureOrNull as? PgenException.ForeignKeyViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onUniqueViolation(
    constraint: Constraint.IUnique? = null,
    block: (PgenException.UniqueViolation) -> Unit,
) = apply {
    (failureOrNull as? PgenException.UniqueViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onCheckViolation(
    constraint: Constraint.Check? = null,
    block: (PgenException.CheckViolation) -> Unit,
) = apply {
    (failureOrNull as? PgenException.CheckViolation)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onSqlViolation(
    constraint: Constraint? = null,
    block: (PgenException.Sql) -> Unit,
) = apply {
    (failureOrNull as? PgenException.Sql)?.also { error ->
        if (constraint?.matches(error) ?: true) block(error)
    }
}

inline fun <T> Result<T, PgenException>.onExclusionViolation(block: (PgenException.ExclusionViolation) -> Unit) =
    apply { (failureOrNull as? PgenException.ExclusionViolation)?.also { block(it) } }

inline fun <T> Result<T, PgenException>.onNoneSqlException(block: (PgenException.Other) -> Unit) =
    apply { (failureOrNull as? PgenException.Other)?.also { block(it) } }

fun <T> Result<T, PgenException>.getOrThrowInternalServerError(msg: String): T = getOr {
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

fun PgenException.toQuatiException() = when (this) {
    is PgenException.Other -> QuatiException.InternalServerError(msg)
    is PgenException.Generic -> QuatiException.InternalServerError("$msg: $prettyMsg", this)

    is PgenException.NotNullViolation, is PgenException.IntegrityConstraintViolation,
    is PgenException.CheckViolation -> QuatiException.BadRequest("$msg: $prettyMsg", this)

    is PgenException.ExclusionViolation, is PgenException.ForeignKeyViolation,
    is PgenException.RestrictViolation, is PgenException.UniqueViolation ->
        QuatiException.Conflict("$msg: $prettyMsg", this)
}
