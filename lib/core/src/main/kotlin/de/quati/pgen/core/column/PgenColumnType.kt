package de.quati.pgen.core.column

import org.jetbrains.exposed.v1.core.ColumnType

public abstract class PgenColumnType<T> : ColumnType<T>() {
    public open val typeInfo: TypeInfo? = null

    public data class TypeInfo(
        val oid: Int,
        val unsignedOid: Long = oid.toLong(),
        val typarray: Int,
        val unsignedTyparrayval: Long = typarray.toLong(),
        val name: String,
        val category: String,
    )
}