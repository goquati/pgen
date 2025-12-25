package de.quati.pgen.shared

@JvmInline
public value class PgenMultiRange<T : Comparable<T>>(
    private val ranges: Set<ClosedRange<T>>
) : Set<ClosedRange<T>> {
    override val size: Int get() = ranges.size
    override fun isEmpty(): Boolean = ranges.isEmpty()
    override fun contains(element: ClosedRange<T>): Boolean = ranges.contains(element)
    override fun iterator(): Iterator<ClosedRange<T>> = ranges.iterator()
    override fun containsAll(elements: Collection<ClosedRange<T>>): Boolean = ranges.containsAll(elements)
}
