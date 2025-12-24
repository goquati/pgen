package de.quati.pgen.intern

import de.quati.pgen.shared.PgenMultiRange


@JvmInline
public value class PgenRawMultiRange(public val ranges: List<PgenRawRange>) {
    public fun toInt4MultiRange(): PgenMultiRange<Int> = PgenMultiRange(ranges.map { it.toInt4Range() }.toSet())
    public fun toInt8MultiRange(): PgenMultiRange<Long> = PgenMultiRange(ranges.map { it.toInt8Range() }.toSet())

    public companion object {
        public fun parse(value: String): PgenRawMultiRange {
            return if (value == "{}")
                PgenRawMultiRange(emptyList())
            else
                value.trimStart('{').trimEnd('}')
                    .split(',').chunked(2)
                    .map { borders -> borders.joinToString(",") }
                    .map { PgenRawRange.parse(it) }
                    .let { PgenRawMultiRange(it) }
        }
    }
}

public fun <T : Comparable<T>> PgenMultiRange<T>.toPostgresqlValue(): String =
    joinToString(separator = ",", prefix = "{", postfix = "}") {
        PgenRange.toPostgresqlValue(it)
    }