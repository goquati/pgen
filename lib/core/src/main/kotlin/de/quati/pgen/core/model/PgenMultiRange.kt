package de.quati.pgen.core.model

@JvmInline
public value class PgenMultiRange<T : Comparable<T>>(
    private val ranges: Set<ClosedRange<T>>
) : Set<ClosedRange<T>> by ranges {

    public fun toPostgresqlValue(): String = ranges.joinToString(separator = ",", prefix = "{", postfix = "}") {
        PgenRange.toPostgresqlValue(it)
    }
}
