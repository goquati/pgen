package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject


public abstract class MultiRangeColumnType<T : Comparable<T>> : ColumnType<MultiRange<T>>() {
    public abstract fun String.parse(): MultiRange<T>

    override fun nonNullValueToString(value: MultiRange<T>): String =
        value.joinToString(separator = ",", prefix = "{", postfix = "}") { it.toPgRangeString() }

    override fun nonNullValueAsDefaultString(value: MultiRange<T>): String =
        "'${nonNullValueToString(value)}'"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = @Suppress("UNCHECKED_CAST") nonNullValueToString(it as MultiRange<T>)
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }

    override fun valueFromDB(value: Any): MultiRange<T>? = when (value) {
        is PGobject -> value.value?.takeIf { it.isNotBlank() }?.parse()
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}