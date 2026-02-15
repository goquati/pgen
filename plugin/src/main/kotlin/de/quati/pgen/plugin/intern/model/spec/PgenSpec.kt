package de.quati.pgen.plugin.intern.model.spec

import kotlinx.serialization.Serializable

@Serializable
internal data class PgenSpec(
    val databases: List<Database>,
) {
    @Serializable
    internal data class Database(
        val name: DbName,
        val tables: List<Table> = emptyList(),
        val enums: List<Enum> = emptyList(),
        val compositeTypes: List<CompositeType> = emptyList(),
        val additionalDomainTypes: List<Column.Type.NonPrimitive.Domain> = emptyList(),
        val statements: List<Statement> = emptyList(),
    ) {
        val domains: List<Column.Type.NonPrimitive.Domain> = tables
            .flatMap { it.columns.map(Column::type) }
            .filterIsInstance<Column.Type.NonPrimitive.Domain>()
            .let { domainsFromColumns -> (domainsFromColumns + additionalDomainTypes).distinct() }
        val allObjects: List<SqlObject> = (tables + enums + compositeTypes + domains).distinct()
        val allTypes = listOf(
            tables.flatMap { it.columns.map { c -> c.type } },
            enums.map { it.type },
            compositeTypes.flatMap { listOf(it.type) + it.columns.map { c -> c.type } },
            additionalDomainTypes
        ).flatten().toSet()
    }
}