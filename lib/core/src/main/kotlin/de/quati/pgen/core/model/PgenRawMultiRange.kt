package de.quati.pgen.core.model


@JvmInline
public value class PgenRawMultiRange(public val ranges: List<PgenRawRange>) {
    public fun toInt4MultiRange(): PgenMultiRange<Int> = PgenMultiRange(ranges.map { it.toInt4Range() }.toSet())
    public fun toInt8MultiRange(): PgenMultiRange<Long> = PgenMultiRange(ranges.map { it.toInt8Range() }.toSet())

    public companion object {
        public fun parse(value: String): PgenRawMultiRange = value.trimStart('{').trimEnd('}')
            .split(',').chunked(2)
            .map { borders -> borders.joinToString(",") }
            .map { PgenRawRange.parse(it) }
            .let { PgenRawMultiRange(it) }
    }
}