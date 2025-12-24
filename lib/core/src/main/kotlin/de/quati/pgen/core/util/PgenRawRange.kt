package de.quati.pgen.core.util


public sealed interface PgenRawRange {
    public data object Empty : PgenRawRange
    public data class Normal(public val start: Border, public val end: Border) : PgenRawRange
    public sealed interface Border {
        public data object Infinity : Border
        public data class Normal(public val value: String, public val inclusive: Boolean) : Border
    }

    public fun toInt4Range(): IntRange = when (this) {
        Empty -> IntRange.EMPTY
        is Normal -> IntRange(
            start = when (start) {
                Border.Infinity -> Int.MIN_VALUE
                is Border.Normal -> start.value.toInt().let { if (start.inclusive) it else it + 1 }
            },
            endInclusive = when (end) {
                Border.Infinity -> Int.MAX_VALUE
                is Border.Normal -> end.value.toInt().let { if (end.inclusive) it else it - 1 }
            },
        )
    }

    public fun toInt8Range(): LongRange = when (this) {
        Empty -> LongRange.EMPTY
        is Normal -> LongRange(
            start = when (start) {
                Border.Infinity -> Long.MIN_VALUE
                is Border.Normal -> start.value.toLong().let { if (start.inclusive) it else it + 1 }
            },
            endInclusive = when (end) {
                Border.Infinity -> Long.MAX_VALUE
                is Border.Normal -> end.value.toLong().let { if (end.inclusive) it else it - 1 }
            },
        )
    }

    public companion object {
        private fun parseRangeBorderStart(value: String): Border {
            if (value.isBlank()) error("invalid range start ''")
            if (value == "(") return Border.Infinity
            return Border.Normal(
                value = value.trimStart('[', '(').takeIf { it.isNotBlank() } ?: error("invalid range start '$this'"),
                inclusive = when (value.first()) {
                    '[' -> true
                    '(' -> false
                    else -> error("Retrieved unexpected range start '$this'")
                }
            )
        }

        private fun parseRangeBorderEnd(value: String): Border {
            if (value.isBlank()) error("invalid range end ''")
            if (value == ")") return Border.Infinity
            return Border.Normal(
                value = value.trimEnd(']', ')').takeIf { it.isNotBlank() } ?: error("invalid range end '$this'"),
                inclusive = when (value.last()) {
                    ']' -> true
                    ')' -> false
                    else -> error("Retrieved unexpected range end '$this'")
                }
            )
        }

        public fun parse(value: String): PgenRawRange {
            if (value == "()") return Empty
            val (startRaw, endRaw) = value.split(",").takeIf { it.size == 2 }
                ?: error("invalid range string '$this'")
            return Normal(
                start = parseRangeBorderStart(startRaw),
                end = parseRangeBorderEnd(endRaw),
            )
        }
    }
}
