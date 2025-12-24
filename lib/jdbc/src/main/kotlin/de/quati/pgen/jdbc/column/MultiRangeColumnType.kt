package de.quati.pgen.jdbc.column

import de.quati.pgen.intern.MultiRangeColumnType
import de.quati.pgen.shared.PgenMultiRange
import de.quati.pgen.intern.PgenRawMultiRange
import de.quati.pgen.intern.toPostgresqlValue
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject


private fun <T : Comparable<T>> fromDb(
    value: Any,
    mapper: (PgenRawMultiRange) -> PgenMultiRange<T>,
): PgenMultiRange<T>? = when (value) {
    is PGobject -> value.value?.takeIf { it.isNotBlank() }
        ?.let { PgenRawMultiRange.parse(it) }
        ?.let { mapper(it) }

    else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
}

public fun Table.int4MultiRange(name: String): Column<PgenMultiRange<Int>> =
    registerColumn(name, Int4MultiRangeColumnType())

public class Int4MultiRangeColumnType : MultiRangeColumnType<Int>(
    sqlType = "INT4MULTIRANGE",
    fromDb = { value -> fromDb(value) { it.toInt4MultiRange() } },
    toDb = {
        PGobject().apply {
            type = "INT4MULTIRANGE"
            this.value = it.toPostgresqlValue()
        }
    }
)

public fun Table.int8MultiRange(name: String): Column<PgenMultiRange<Long>> =
    registerColumn(name, Int8MultiRangeColumnType())

public class Int8MultiRangeColumnType : MultiRangeColumnType<Long>(
    sqlType = "INT8MULTIRANGE",
    fromDb = { value -> fromDb(value) { it.toInt8MultiRange() } },
    toDb = {
        PGobject().apply {
            type = "INT8MULTIRANGE"
            this.value = it.toPostgresqlValue()
        }
    }
)