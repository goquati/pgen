package de.quati.pgen.plugin.intern.model.sql

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.util.codegen.SpecContext
import de.quati.pgen.plugin.intern.util.codegen.toSqlObjectNameOrNull
import kotlinx.serialization.Serializable


@Serializable
internal data class PgenSpec(
    val tables: List<Table>,
    val enums: List<Enum>,
    val compositeTypes: List<CompositeType>,
    val additionalDomainTypes: List<Column.Type.NonPrimitive.Domain> = emptyList(),
    val statements: List<Statement>,
) {
    val allObjects: List<SqlObject> get() = (tables + enums + compositeTypes + domains).distinct()
    val domains: List<Column.Type.NonPrimitive.Domain>
        get() {
            val domainsFromColumns = tables
                .flatMap { it.columns.map(Column::type) }
                .filterIsInstance<Column.Type.NonPrimitive.Domain>()
            return (domainsFromColumns + additionalDomainTypes).distinct()
        }

    private val allTypes
        get() = listOf(
            tables.flatMap { it.columns.map { c -> c.type } },
            enums.map { it.type },
            compositeTypes.flatMap { listOf(it.type) + it.columns.map { c -> c.type } },
            additionalDomainTypes
        ).flatten().toSet()

    fun toSpecContext(config: Config): SpecContext {
        val customTypeMappings = config.dbConfigs.flatMap(Config.Db::columnTypeMappings).associateBy { it.name }
        val refMappings = allTypes.mapNotNull { type ->
            type.toSqlObjectNameOrNull()?.let { it to type }
        }.toMap()

        return SpecContext.Base(refMappings + customTypeMappings)
    }
}
