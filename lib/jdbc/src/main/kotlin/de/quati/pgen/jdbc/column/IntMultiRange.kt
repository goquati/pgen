package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table


public class Int4MultiRangeColumnType : MultiRangeColumnType<Int>() {
    override fun sqlType(): String = "INT4MULTIRANGE"
    override fun String.parse(): MultiRange<Int> = parseMultiRange().toInt4MultiRange()
}

public class Int8MultiRangeColumnType : MultiRangeColumnType<Long>() {
    override fun sqlType(): String = "INT8MULTIRANGE"
    override fun String.parse(): MultiRange<Long> = parseMultiRange().toInt8MultiRange()
}

public fun Table.int4MultiRange(name: String): Column<MultiRange<Int>> =
    registerColumn(name, Int4MultiRangeColumnType())

public fun Table.int8MultiRange(name: String): Column<MultiRange<Long>> =
    registerColumn(name, Int8MultiRangeColumnType())

internal fun List<RawRange>.toInt4MultiRange(): MultiRange<Int> = MultiRange(map { it.toInt4Range() }.toSet())
internal fun List<RawRange>.toInt8MultiRange(): MultiRange<Long> = MultiRange(map { it.toInt8Range() }.toSet())
