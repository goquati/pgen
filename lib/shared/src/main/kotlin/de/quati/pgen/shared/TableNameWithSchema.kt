package de.quati.pgen.shared


@JvmInline
public value class TableNameWithSchema(public val name: String) {
    public constructor(schema: String, table: String) : this("$schema.$table")

    public val table: String get() = name.substringAfter('.')
    public val schema: String get() = name.substringBefore('.')
    override fun toString(): String = name

    init {
        val (schema, table) = name.split('.', limit = 2)
            .takeIf { it.size == 2 }
            ?: error("Invalid table name: $name, expected <schema>.<table>")

        requireValidPgIdentifier(schema, what = "schema")
        requireValidPgIdentifier(table, what = "table")
    }
}
