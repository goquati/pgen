package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.model.PgenMultiRange
import de.quati.pgen.core.model.PgenRawMultiRange
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table


public abstract class MultiRangeColumnType<T : Comparable<T>> : ColumnType<PgenMultiRange<T>>() {
    public abstract fun parse(value: String): PgenMultiRange<T>

    override fun nonNullValueToString(value: PgenMultiRange<T>): String = value.toPostgresqlValue()

    override fun nonNullValueAsDefaultString(value: PgenMultiRange<T>): String =
        "'${nonNullValueToString(value)}'"

    override fun valueFromDB(value: Any): PgenMultiRange<T>? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }?.let { parse(it) }
        else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
    }
}


public fun Table.int4MultiRange(name: String): Column<PgenMultiRange<Int>> =
    registerColumn(name, Int4MultiRangeColumnType())

public class Int4MultiRangeColumnType : MultiRangeColumnType<Int>() {
    override fun sqlType(): String = "INT4MULTIRANGE"
    override fun parse(value: String): PgenMultiRange<Int> = PgenRawMultiRange.parse(value).toInt4MultiRange()
}

public fun Table.int8MultiRange(name: String): Column<PgenMultiRange<Long>> =
    registerColumn(name, Int8MultiRangeColumnType())

public class Int8MultiRangeColumnType : MultiRangeColumnType<Long>() {
    override fun sqlType(): String = "INT8MULTIRANGE"
    override fun parse(value: String): PgenMultiRange<Long> = PgenRawMultiRange.parse(value).toInt8MultiRange()
}
