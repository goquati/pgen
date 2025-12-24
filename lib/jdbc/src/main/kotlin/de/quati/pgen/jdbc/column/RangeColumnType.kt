package de.quati.pgen.jdbc.column

import de.quati.pgen.core.util.PgenRange
import de.quati.pgen.core.util.PgenRawRange
import de.quati.pgen.core.column.RangeColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject


private fun <T : Comparable<T>, R : ClosedRange<T>> fromDb(
    value: Any,
    mapper: (PgenRawRange) -> R,
): R? = when (value) {
    is PGobject -> value.value?.takeIf { it.isNotBlank() }
        ?.let { PgenRawRange.parse(it) }
        ?.let { mapper(it) }

    else -> error("Retrieved unexpected value of type ${value::class.simpleName}")
}

public fun Table.int4Range(name: String): Column<IntRange> = registerColumn(name, Int4RangeColumnType())
public class Int4RangeColumnType : RangeColumnType<Int, IntRange>(
    sqlType = "INT4RANGE",
    fromDb = { value -> fromDb(value) { it.toInt4Range() } },
    toDb = {
        PGobject().apply {
            type = "INT4RANGE"
            this.value = PgenRange.toPostgresqlValue(it)
        }
    },
)

public fun Table.int8Range(name: String): Column<LongRange> = registerColumn(name, Int8RangeColumnType())
public class Int8RangeColumnType : RangeColumnType<Long, LongRange>(
    sqlType = "INT8RANGE",
    fromDb = { value -> fromDb(value) { it.toInt8Range() } },
    toDb = {
        PGobject().apply {
            type = "INT8RANGE"
            this.value = PgenRange.toPostgresqlValue(it)
        }
    },
)
