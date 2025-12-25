package de.quati.pgen.plugin.intern.model.sql

import kotlinx.serialization.Serializable

@Serializable
internal data class CompositeType(
    override val name: SqlObjectName,
    val columns: List<Column>,
) : SqlObject {
    val type get() = Column.Type.NonPrimitive.Composite(name)
}