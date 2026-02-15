package de.quati.pgen.plugin.intern.service.db

import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.Column.Type
import de.quati.pgen.plugin.intern.model.spec.CompositeType
import de.quati.pgen.plugin.intern.model.spec.Enum
import de.quati.pgen.plugin.intern.model.spec.SqlObjectName
import de.quati.pgen.plugin.intern.model.spec.Statement
import de.quati.pgen.plugin.intern.model.spec.Table
import java.io.Closeable

internal sealed class DbService(
    columnTypeMappings: Collection<Type.CustomType>
) : Closeable {
    private val columnTypeMappings = columnTypeMappings.associateBy { it.name }
    abstract fun getStatements(rawStatements: List<Statement.Raw>): List<Statement>
    abstract fun getDomainTypes(filter: SqlObjectFilter): List<Type.NonPrimitive.Domain>
    abstract fun getEnums(enumNames: Set<SqlObjectName>): List<Enum>
    protected abstract fun getColumns(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>>
    protected abstract fun getCompositeTypeFields(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>>
    protected abstract fun getPrimaryKeys(filter: SqlObjectFilter): Map<SqlObjectName, Table.PrimaryKey>
    protected abstract fun getForeignKeys(filter: SqlObjectFilter): Map<SqlObjectName, List<Table.ForeignKey>>
    protected abstract fun getCheckConstraints(filter: SqlObjectFilter): Map<SqlObjectName, List<Table.CheckConstraint>>
    protected abstract fun getUniqueConstraints(
        filter: SqlObjectFilter,
    ): Map<SqlObjectName, List<Table.UniqueConstraint>>

    protected abstract fun TypeData.getColumnTypeInner(): Type


    fun getTablesWithForeignTables(filter: SqlObjectFilter): List<Table> {
        return buildList {
            var currentFilter = filter
            while (!currentFilter.isEmpty()) {
                addAll(getTables(currentFilter))
                val tablesNames = map { it.name }.toSet()
                val foreignTableNames = flatMap { t -> t.foreignKeys.map { it.targetTable } }.toSet()
                val missingTableNames = foreignTableNames - tablesNames
                currentFilter = SqlObjectFilter.Objects(missingTableNames)
            }
        }
    }


    fun getCompositeTypes(compositeTypeNames: Set<SqlObjectName>): List<CompositeType> {
        val filter = SqlObjectFilter.Objects(compositeTypeNames)
        if (filter.isEmpty()) return emptyList()
        return getCompositeTypeFields(filter).map { (tableName, columns) ->
            CompositeType(
                name = tableName,
                columns = columns.sortedBy { it.pos },
            )
        }
    }

    protected fun getTables(filter: SqlObjectFilter): List<Table> {
        if (filter.isEmpty()) return emptyList()
        val columns = getColumns(filter)
        val primaryKeys = getPrimaryKeys(filter)
        val foreignKeys = getForeignKeys(filter)
        val uniqueConstraints = getUniqueConstraints(filter)
        val checkConstraints = getCheckConstraints(filter)
        val tableNames = columns.keys + primaryKeys.keys + foreignKeys.keys

        return tableNames.map { tableName ->
            Table(
                name = tableName,
                columns = columns[tableName]?.sortedBy { it.pos } ?: emptyList(),
                primaryKey = primaryKeys[tableName],
                foreignKeys = foreignKeys[tableName]?.sortedBy { it.name } ?: emptyList(),
                uniqueConstraints = uniqueConstraints[tableName]?.sortedBy { it.name } ?: emptyList(),
                checkConstraints = checkConstraints[tableName]?.sortedBy { it.name } ?: emptyList()
            )
        }
    }

    protected fun TypeData.getColumnType(): Type {
        val rawType = SqlObjectName(
            schema = innerTypeSchema,
            name = innerTypeName,
        ).let(columnTypeMappings::get)
            ?: getColumnTypeInner()

        return run {
            Type.NonPrimitive.Domain(
                ref = SqlObjectName(
                    schema = domainSchema ?: return@run null,
                    name = domainName ?: return@run null,
                ),
                base = rawType,
            )
        } ?: rawType
    }

    protected fun ColumnData.parseColumn(): Pair<SqlObjectName, Column> {
        val tableName = SqlObjectName(
            schema = tableSchema,
            name = tableName,
        )
        val type = typeData.getColumnType()
        return tableName to Column(
            pos = pos,
            name = Column.Name(columnName),
            type = type,
            nullable = isNullable,
            defaultExpr = columnDefault,
        )
    }
}