package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table


public class Int4RangeColumnType : RangeColumnType<Int, IntRange>() {
    override fun sqlType(): String = "INT4RANGE"
    override fun String.parse(): IntRange = parseRange().toInt4Range()
}

public class Int8RangeColumnType : RangeColumnType<Long, LongRange>() {
    override fun sqlType(): String = "INT8RANGE"
    override fun String.parse(): LongRange = parseRange().toInt8Range()
}

public fun Table.int4Range(name: String): Column<IntRange> = registerColumn(name, Int4RangeColumnType())
public fun Table.int8Range(name: String): Column<LongRange> = registerColumn(name, Int8RangeColumnType())


internal fun RawRange.toInt4Range(): IntRange = when (this) {
    RawRange.Empty -> IntRange.EMPTY
    is RawRange.Normal -> IntRange(
        start = when (start) {
            RawRangeBorder.Infinity -> Int.MIN_VALUE
            is RawRangeBorder.Normal -> start.value.toInt().let { if (start.inclusive) it else it + 1 }
        },
        endInclusive = when (end) {
            RawRangeBorder.Infinity -> Int.MAX_VALUE
            is RawRangeBorder.Normal -> end.value.toInt().let { if (end.inclusive) it else it - 1 }
        },
    )
}

internal fun RawRange.toInt8Range(): LongRange = when (this) {
    RawRange.Empty -> LongRange.EMPTY
    is RawRange.Normal -> LongRange(
        start = when (start) {
            RawRangeBorder.Infinity -> Long.MIN_VALUE
            is RawRangeBorder.Normal -> start.value.toLong().let { if (start.inclusive) it else it + 1 }
        },
        endInclusive = when (end) {
            RawRangeBorder.Infinity -> Long.MAX_VALUE
            is RawRangeBorder.Normal -> end.value.toLong().let { if (end.inclusive) it else it - 1 }
        },
    )
}
