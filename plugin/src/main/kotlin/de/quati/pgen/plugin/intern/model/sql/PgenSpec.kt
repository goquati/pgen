package de.quati.pgen.plugin.intern.model.sql

import kotlinx.serialization.Serializable


@Serializable
internal data class PgenSpec(
    val tables: List<Table>,
    val enums: List<Enum>,
    val compositeTypes: List<CompositeType>,
    val statements: List<Statement>,
) {
    val domains
        get() = tables
            .flatMap { it.columns.map(Column::type) }
            .filterIsInstance<Column.Type.NonPrimitive.Domain>()
}
