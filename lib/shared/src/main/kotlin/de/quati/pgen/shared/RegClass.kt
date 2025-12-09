package de.quati.pgen.shared

@JvmInline
public value class RegClass(public val name: String) {
    public val schema: String get() = name.substringBefore('.')
    public val table: String get() = name.substringAfter('.')

    init {
        require(name.count { it == '.' } == 1) { "RegClass name must be of format schema.table" }
    }

    override fun toString(): String = name

    public companion object {
        public fun of(name: String): RegClass = if ('.' !in name) RegClass("public.$name") else RegClass(name)
    }
}
