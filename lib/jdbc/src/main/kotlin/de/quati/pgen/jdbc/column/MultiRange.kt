package de.quati.pgen.jdbc.column


@JvmInline
public value class MultiRange<T : Comparable<T>>(
    private val ranges: Set<ClosedRange<T>>
) : Set<ClosedRange<T>> by ranges


internal fun String.parseMultiRange(): List<RawRange> = trimStart('{').trimEnd('}')
    .split(',').chunked(2)
    .map { borders -> borders.joinToString(",") }
    .map { it.parseRange() }

internal sealed interface RawRange {
    data object Empty : RawRange
    data class Normal(val start: RawRangeBorder, val end: RawRangeBorder) : RawRange
}

internal sealed interface RawRangeBorder {
    data object Infinity : RawRangeBorder
    data class Normal(
        val value: String,
        val inclusive: Boolean,
    ) : RawRangeBorder
}