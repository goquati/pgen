package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.PgenSpec
import de.quati.pgen.plugin.intern.service.DbService
import de.quati.pgen.plugin.intern.codegen.toSqlObjectNameOrNull
import de.quati.pgen.plugin.intern.util.parseStatements
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText


internal fun generateSpec(config: Config) {
    val databases = config.dbConfigs.map { configDb -> generateDbSpec(configDb) }
    val spec = PgenSpec(
        databases = databases.sortedBy { it.name },
    )
    val yamlStr = spec.encodeToYaml()
    config.specFilePath.createParentDirectories().writeText(yamlStr)
}

internal fun generateDbSpec(config: Config.Db): PgenSpec.Database {
    return DbService(
        columnTypeMappings = config.columnTypeMappings,
        connectionConfig = config.connection ?: error("no DB connection config defined")
    ).use { dbService ->
        val statements = dbService.getStatements(parseStatements(config.statementScripts))
        val tables = dbService.getTablesWithForeignTables(config.tableFilter)
        val allColumnTypes = tables.asSequence().flatMap { it.columns }.map { it.type }
            .map { if (it is Column.Type.NonPrimitive.Array) it.element else it }.toSet()

        val additionalDomainTypes = with(dbService) {
            val actualNames = allColumnTypes.mapNotNull { it.toSqlObjectNameOrNull() }.toSet()
            val refNames = allColumnTypes.filterIsInstance<Column.Type.Reference>().map { it.name }.toSet()
            val missingNames = refNames - actualNames
            val filter = SqlObjectFilter.Objects(missingNames)
            val foundObjects = dbService.getDomainTypes(filter)
            val notFoundNames = missingNames - foundObjects.map { it.ref }.toSet()
            if (notFoundNames.isNotEmpty())
                error("domain types $notFoundNames are not found")
            foundObjects
        }

        val enumNames =
            allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Enum>().map { it.ref }.toSet()
        val compositeTypeNames =
            allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Composite>().map { it.ref }.toSet()
        val enums = dbService.getEnums(enumNames)
        val compositeTypes = dbService.getCompositeTypes(compositeTypeNames)
        PgenSpec.Database(
            name = config.dbName,
            tables = tables.sortedBy { it.name },
            enums = enums.sortedBy { it.name },
            compositeTypes = compositeTypes.sortedBy { it.name },
            additionalDomainTypes = additionalDomainTypes.sortedBy { it.ref },
            statements = statements.sortedBy { it.name },
        )
    }
}
