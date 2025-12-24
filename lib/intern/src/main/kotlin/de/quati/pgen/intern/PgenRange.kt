package de.quati.pgen.intern

public object PgenRange {
    public fun <T : Comparable<T>, R : ClosedRange<T>> toPostgresqlValue(range: R): String =
        "[${range.start},${range.endInclusive}]"
}
