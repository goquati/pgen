package de.quati.pgen.core.util

import de.quati.pgen.shared.StringLike
import de.quati.kotlin.util.QuatiException
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.QueryBuilder

public object IsInsert : Expression<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("xmax = 0")
    }
}

public object IsUpdate : Expression<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("xmax != 0")
    }
}

public operator fun <T> ResultRow.get(column: Column<T>, alias: Alias<*>?): T = when (alias) {
    null -> this[column]
    else -> this[alias[column]]
}

public fun compoundAnd(con: Op<Boolean>?, vararg cons: Op<Boolean>?): Op<Boolean> =
    (listOf(con) + cons).filterNotNull().let {
        when (it.size) {
            0 -> Op.TRUE
            1 -> it.single()
            else -> it.compoundAnd()
        }
    }

public fun compoundOr(con: Op<Boolean>?, vararg cons: Op<Boolean>?): Op<Boolean> =
    (listOf(con) + cons).filterNotNull().let {
        when (it.size) {
            0 -> Op.TRUE
            1 -> it.single()
            else -> it.compoundOr()
        }
    }

public fun <T : Any> arrayAgg(
    elementColumnType: ColumnType<T>,
    exp: Expression<out T?>,
): CustomFunction<List<T?>> = CustomFunction<List<T?>>(
    functionName = "array_agg",
    columnType = ArrayColumnType(elementColumnType),
    exp,
)

public fun arrayAgg(exp: Expression<out String?>): CustomFunction<List<String?>> =
    arrayAgg(TextColumnType(), exp)

private class LikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "LIKE")
private class InsensitiveLikeOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "ILIKE")
private class ContainsOp<T : List<String>?>(expr1: Expression<T>, expr2: Expression<*>) :
    ComparisonOp(expr2, anyFrom(expr1), "=")

public infix fun <T : StringLike?> ExpressionWithColumnType<T>.like(pattern: String): Op<Boolean> =
    LikeOp(this, QueryParameter(pattern, TextColumnType()))

@JvmName("iLikeString")
public infix fun <T : String?> ExpressionWithColumnType<T>.iLike(pattern: T): Op<Boolean> =
    InsensitiveLikeOp(this, QueryParameter(pattern, columnType))

@JvmName("iLikeStringLike")
public infix fun <T : StringLike?> ExpressionWithColumnType<T>.iLike(pattern: String): Op<Boolean> =
    InsensitiveLikeOp(this, QueryParameter(pattern, TextColumnType()))

public infix fun <T : List<String>?> ExpressionWithColumnType<T>.arrayContains(
    pattern: String,
): Op<Boolean> = ContainsOp(
    expr1 = this,
    expr2 = QueryParameter(value = pattern, columnType = TextColumnType()),
)


public sealed interface DeleteSingleResult {
    public fun getOrThrow(msg: String? = null): Unit = when (this) {
        Success -> Unit
        None -> throw QuatiException.NotFound((msg?.let { "$it - " } ?: "") + "nothing to delete")
        TooMany -> throw QuatiException.Conflict((msg?.let { "$it - " } ?: "") + "multiple matches found")
    }

    public data object None : DeleteSingleResult
    public data object Success : DeleteSingleResult
    public data object TooMany : DeleteSingleResult
}

public sealed interface UpdateSingleResult {
    public fun getOrThrow(msg: String): ResultRow = when (this) {
        is Success -> data
        None -> throw QuatiException.NotFound("$msg — nothing to update")
        TooMany -> throw QuatiException.Conflict("$msg — multiple matches found")
    }

    public fun getOrNull(): ResultRow? = when (this) {
        is Success -> data
        None -> null
        TooMany -> null
    }

    public data object None : UpdateSingleResult
    public class Success(public val data: ResultRow) : UpdateSingleResult
    public data object TooMany : UpdateSingleResult
}
