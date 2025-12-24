package de.quati.pgen.shared

@JvmInline
public value class PgenMultiRange<T : Comparable<T>>(
    private val ranges: Set<ClosedRange<T>>
) : Set<ClosedRange<T>> by ranges
