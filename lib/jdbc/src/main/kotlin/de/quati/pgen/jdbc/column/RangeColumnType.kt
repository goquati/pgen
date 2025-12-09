package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

public abstract class RangeColumnType<T : Comparable<T>, R : ClosedRange<T>> : ColumnType<R>() {
    public abstract fun String.parse(): R

    override fun nonNullValueToString(value: R): String = value.toPgRangeString()

    override fun nonNullValueAsDefaultString(value: R): String =
        "'${nonNullValueToString(value)}'"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = @Suppress("UNCHECKED_CAST") nonNullValueToString(it as R)
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }

    override fun valueFromDB(value: Any): R? = when (value) {
        is PGobject -> value.value?.takeIf { it.isNotBlank() }?.parse()
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}


internal fun String.parseRangeBorderStart(): RawRangeBorder {
    if (isBlank()) error("invalid range start ''")
    if (this == "(") return RawRangeBorder.Infinity
    return RawRangeBorder.Normal(
        value = trimStart('[', '(').takeIf { it.isNotBlank() } ?: error("invalid range start '$this'"),
        inclusive = when (first()) {
            '[' -> true
            '(' -> false
            else -> error("Retrieved unexpected range start '$this'")
        }
    )
}

internal fun String.parseRangeBorderEnd(): RawRangeBorder {
    if (isBlank()) error("invalid range end ''")
    if (this == ")") return RawRangeBorder.Infinity
    return RawRangeBorder.Normal(
        value = trimStart(']', ')').takeIf { it.isNotBlank() } ?: error("invalid range end '$this'"),
        inclusive = when (last()) {
            ']' -> true
            ')' -> false
            else -> error("Retrieved unexpected range end '$this'")
        }
    )
}

internal fun String.parseRange(): RawRange {
    if (this == "()") return RawRange.Empty
    val (startRaw, endRaw) = split(",").takeIf { it.size == 2 }
        ?: error("invalid range string '$this'")
    return RawRange.Normal(
        start = startRaw.parseRangeBorderStart(),
        end = endRaw.parseRangeBorderEnd(),
    )
}

internal fun ClosedRange<*>.toPgRangeString(): String = "[$start,$endInclusive]"
