package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter
import de.quati.pgen.plugin.intern.model.sql.Column
import de.quati.pgen.plugin.intern.model.sql.PgenSpec
import de.quati.pgen.plugin.intern.model.sql.Statement
import de.quati.pgen.plugin.intern.service.DbService
import de.quati.pgen.plugin.intern.util.codegen.toSqlObjectNameOrNull
import de.quati.pgen.plugin.intern.util.parseStatements
import kotlinx.serialization.encodeToString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText


internal fun generateSpec(config: Config) {
    val specData = config.dbConfigs.map { configDb ->
        DbService(
            dbName = configDb.dbName,
            columnTypeMappings = configDb.columnTypeMappings,
            connectionConfig = configDb.connection ?: error("no DB connection config defined")
        ).use { dbService ->
            val statements = dbService.getStatements(parseStatements(configDb.statementScripts))
            val tables = dbService.getTablesWithForeignTables(configDb.tableFilter)
            val allColumnTypes = tables.asSequence().flatMap { it.columns }.map { it.type }
                .map { if (it is Column.Type.NonPrimitive.Array) it.elementType else it }.toSet()

            val additionalDomainTypes = with(dbService) {
                val actualNames = allColumnTypes.mapNotNull { it.toSqlObjectNameOrNull() }.toSet()
                val refNames = allColumnTypes.filterIsInstance<Column.Type.Reference>().map { it.name }.toSet()
                val missingNames = refNames - actualNames
                val filter = SqlObjectFilter.Objects(missingNames)
                val foundObjects = dbService.getDomainTypes(filter)
                val notFoundNames = missingNames - foundObjects.map { it.name }.toSet()
                if (notFoundNames.isNotEmpty())
                    error("domain types $notFoundNames are not found")
                foundObjects
            }

            val enumNames =
                allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Enum>().map { it.name }.toSet()
            val compositeTypeNames =
                allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Composite>().map { it.name }.toSet()
            val enums = dbService.getEnums(enumNames)
            val compositeTypes = dbService.getCompositeTypes(compositeTypeNames)
            PgenSpec(
                tables = tables,
                enums = enums,
                compositeTypes = compositeTypes,
                additionalDomainTypes = additionalDomainTypes,
                statements = statements,
            )
        }
    }
    val spec = PgenSpec(
        tables = specData.flatMap(PgenSpec::tables).sorted(),
        enums = specData.flatMap(PgenSpec::enums).sorted(),
        compositeTypes = specData.flatMap(PgenSpec::compositeTypes).sorted(),
        additionalDomainTypes = specData.flatMap(PgenSpec::additionalDomainTypes).sorted(),
        statements = specData.flatMap(PgenSpec::statements).sortedBy(Statement::name),
    )
    config.specFilePath.createParentDirectories().writeText(yaml.encodeToString(spec))
}
