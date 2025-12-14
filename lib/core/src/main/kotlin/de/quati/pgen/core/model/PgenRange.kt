package de.quati.pgen.core.model

public object PgenRange {
    public fun <T : Comparable<T>, R : ClosedRange<T>> toPostgresqlValue(range: R): String =
        "[${range.start},${range.endInclusive}]"
}
