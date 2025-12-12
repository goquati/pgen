package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

public abstract class RangeColumnType<T : Comparable<T>, R : ClosedRange<T>> : ColumnType<R>() {
    public abstract fun parse(value: String): R

    override fun nonNullValueToString(value: R): String = "[${value.start},${value.endInclusive}]"

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
        is PGobject -> value.value?.takeIf { it.isNotBlank() }?.let { parse(it) }
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}


public fun Table.int4Range(name: String): Column<IntRange> = registerColumn(name, Int4RangeColumnType())
public class Int4RangeColumnType : RangeColumnType<Int, IntRange>() {
    override fun sqlType(): String = "INT4RANGE"
    override fun parse(value: String): IntRange = PgenRawRange.parse(value).toInt4Range()
}

public fun Table.int8Range(name: String): Column<LongRange> = registerColumn(name, Int8RangeColumnType())
public class Int8RangeColumnType : RangeColumnType<Long, LongRange>() {
    override fun sqlType(): String = "INT8RANGE"
    override fun parse(value: String): LongRange = PgenRawRange.parse(value).toInt8Range()
}
