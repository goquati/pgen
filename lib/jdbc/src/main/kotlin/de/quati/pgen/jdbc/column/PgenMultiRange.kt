package de.quati.pgen.jdbc.column

@JvmInline
public value class PgenMultiRange<T : Comparable<T>>(
    private val ranges: Set<ClosedRange<T>>
) : Set<ClosedRange<T>> by ranges
