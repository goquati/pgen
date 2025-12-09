package de.quati.pgen.plugin.model.sql

import kotlinx.serialization.Serializable


@Serializable
data class PgenSpec(
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
