package de.quati.pgen.plugin.intern.model.sql

import kotlinx.serialization.Serializable

@Serializable
internal data class Enum(
    override val name: SqlObjectName,
    val fields: List<String>,
) : SqlObject {
    val type get() = Column.Type.NonPrimitive.Enum(name)

}
