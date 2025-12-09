package de.quati.pgen.plugin.service

import de.quati.pgen.plugin.dsl.execute
import de.quati.pgen.plugin.dsl.executeQuery
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.config.SqlObjectFilter
import de.quati.pgen.plugin.model.sql.*
import de.quati.pgen.plugin.model.sql.Enum
import de.quati.pgen.plugin.model.sql.Column.Type
import de.quati.pgen.plugin.model.sql.Column.Type.*
import de.quati.pgen.plugin.model.sql.Column.Type.NonPrimitive.Domain
import org.postgresql.util.PGobject
import java.io.Closeable
import java.sql.DriverManager
import java.sql.ResultSet

class DbService(
    val dbName: DbName,
    connectionConfig: Config.Db.DbConnectionConfig
) : Closeable {
    private val connection = DriverManager.getConnection(
        connectionConfig.url,
        connectionConfig.user,
        connectionConfig.password,
    )

    fun getStatements(rawStatements: List<Statement.Raw>): List<Statement> {
        if (rawStatements.isEmpty()) return emptyList()
        fun Statement.Raw.preparedStmt() = "pgen_prepare_stmt_${name.lowercase()}"
        fun Statement.Raw.tempTable() = "pgen_temp_table_${name.lowercase()}"

        val statements = rawStatements.map { raw ->
            val inputTypes = runCatching {
                connection.execute("PREPARE ${raw.preparedStmt()} AS\n${raw.preparedPsql};")
                val result = connection.executeQuery(
                    """
                    SELECT parameter_types as types
                    FROM pg_prepared_statements
                    WHERE name = '${raw.preparedStmt()}';
                    """.trimIndent()
                ) { rs -> (rs.getArray("types").array as Array<*>).map { (it as? PGobject)?.value!! } }
                    .single().map { getPrimitiveType(it) }
                connection.execute("DEALLOCATE ${raw.preparedStmt()};")
                result
            }.getOrElse { throw Exception("Failed to extract input types of statement '${raw.name}': ${it.message}") }

            if (inputTypes.size != raw.uniqueSortedVariables.size)
                throw Exception("unexpected number of input columns in statement '${raw.name}'")

            val columns = runCatching {
                connection.execute("CREATE TEMP TABLE ${raw.tempTable()} AS\n${raw.sql};")
                val result = getColumns(SqlObjectFilter.TempTable(setOf(raw.tempTable()))).values.single()
                connection.execute("DROP TABLE ${raw.tempTable()};")
                result
            }.getOrElse { throw Exception("Failed to extract output types of statement '${raw.name}': ${it.message}") }

            Statement(
                name = SqlStatementName(dbName = dbName, name = raw.name),
                cardinality = raw.cardinality,
                variables = raw.allVariables,
                variableTypes = raw.uniqueSortedVariables.zip(inputTypes).toMap(),
                columns = columns.map { c ->
                    if (c.name.value in raw.nonNullColumns) c.copy(isNullable = false) else c
                },
                sql = raw.preparedSql,
            )
        }

        val duplicateStatementNames = statements.groupingBy { it.name.prettyName }
            .eachCount().filter { it.value > 1 }.keys
        if (duplicateStatementNames.isNotEmpty())
            error("statements with duplicate names found: $duplicateStatementNames")
        return statements
    }

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

    private fun getTables(filter: SqlObjectFilter): List<Table> {
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
                uniqueConstraints = uniqueConstraints[tableName]?.sorted() ?: emptyList(),
                checkConstraints = checkConstraints[tableName]?.sorted() ?: emptyList()
            )
        }
    }

    private fun ResultSet.getColumnType(
        udtNameOverride: String? = null,
        columnTypeCategoryOverride: String? = null,
    ): Type {
        val schema = dbName.toSchema(getString("column_type_schema")!!)
        val columnTypeName = udtNameOverride ?: getString("column_type_name")!!
        val columnTypeCategory = columnTypeCategoryOverride ?: getString("column_type_category")!!
        if (columnTypeName.startsWith("_")) return NonPrimitive.Array(
            getColumnType(
                udtNameOverride = columnTypeName.removePrefix("_"),
                columnTypeCategoryOverride = getString("column_element_type_category")!!,
            )
        )

        if (schema != dbName.schemaPgCatalog) {
            fun unknownError(): Nothing =
                error("Unknown column type '$columnTypeCategory' for column type '$schema:$columnTypeName'")
            return when (columnTypeCategory) {
                "E" -> NonPrimitive.Enum(SqlObjectName(schema = schema, name = columnTypeName))
                "C" -> NonPrimitive.Composite(SqlObjectName(schema = schema, name = columnTypeName))
                "U" -> {
                    when (columnTypeName) {
                        NonPrimitive.PgVector.VECTOR_NAME -> NonPrimitive.PgVector(schema = schema.schemaName)
                        else -> unknownError()
                    }
                }

                else -> unknownError()
            }
        }
        return when (columnTypeName) {
            "numeric" -> {
                val precision = getInt("numeric_precision").takeIf { !wasNull() }
                val scale = getInt("numeric_scale").takeIf { !wasNull() }
                if (precision != null && scale != null)
                    NonPrimitive.Numeric(precision = precision, scale = scale)
                else if (precision == null && scale == null)
                    Primitive.UNCONSTRAINED_NUMERIC
                else
                    error("invalid numeric type, precision: $precision, scale: $scale")
            }

            else -> getPrimitiveType(columnTypeName)
        }
    }

    private fun getColumns(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>> {
        if (filter.isEmpty()) return emptyMap()
        return connection.executeQuery(
            """
            SELECT
                c.ordinal_position as pos,
                c.table_schema AS table_schema,
                c.table_name AS table_name,
                c.column_name AS column_name,
                c.domain_schema as domain_schema,
                c.domain_name as domain_name,
                c.is_nullable AS is_nullable,
                c.udt_schema AS column_type_schema,
                c.udt_name AS column_type_name,
                c.numeric_precision AS numeric_precision,
                c.numeric_scale AS numeric_scale,
                c.column_default AS column_default,
                ty.typcategory AS column_type_category,
                tye.typcategory AS column_element_type_category
            FROM information_schema.columns AS c
            JOIN pg_catalog.pg_namespace AS na
                ON c.udt_schema = na.nspname
            JOIN pg_catalog.pg_type AS ty
                ON ty.typnamespace = na.oid
                    AND ty.typname = c.udt_name
            LEFT JOIN pg_catalog.pg_type AS tye
                ON ty.typelem != 0
                    AND tye.oid = ty.typelem
            WHERE ${filter.toFilterString(schemaField = "c.table_schema", tableField = "c.table_name")};
            """
        ) { it.parseColumn() }.groupBy({ it.first }, { it.second })
    }

    private fun getCompositeTypeFields(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>> {
        return connection.executeQuery(
            """
            SELECT a.attnum                                                              AS pos,
                   clsn.nspname                                                          AS table_schema,
                   cls.relname                                                           AS table_name,
                   a.attname                                                             AS column_name,
                   CASE
                       WHEN at.typtype = 'd'::"char" THEN atn.nspname
                       ELSE NULL::name
                       END::information_schema.sql_identifier                            AS domain_schema,
                   CASE
                       WHEN at.typtype = 'd'::"char" THEN at.typname
                       ELSE NULL::name
                       END::information_schema.sql_identifier                            AS domain_name,
                   true                                                                  AS is_nullable,
                   COALESCE(nbt.nspname, atn.nspname)::information_schema.sql_identifier AS column_type_schema,
                   COALESCE(bt.typname, at.typname)::information_schema.sql_identifier   AS column_type_name,
                   information_schema._pg_numeric_precision(
                           information_schema._pg_truetypid(a.*, at.*),
                           information_schema._pg_truetypmod(a.*, at.*)
                   )::information_schema.cardinal_number                                 AS numeric_precision,
                   information_schema._pg_numeric_scale(
                           information_schema._pg_truetypid(a.*, at.*),
                           information_schema._pg_truetypmod(a.*, at.*)
                   )::information_schema.cardinal_number                                 AS numeric_scale,
                   NULL                                                                  AS column_default,
                   at.typcategory                                                        AS column_type_category,
                   ate.typcategory                                                       AS column_element_type_category
            FROM pg_catalog.pg_type AS t
                     JOIN pg_catalog.pg_class AS cls
                          ON cls.oid = t.typrelid
                     join pg_namespace as clsn
                          on cls.relnamespace = clsn.oid
                     JOIN pg_catalog.pg_attribute AS a
                          ON a.attrelid = cls.oid AND a.attnum > 0 AND NOT a.attisdropped
                     JOIN pg_catalog.pg_type AS at
                          ON at.oid = a.atttypid
                     LEFT JOIN pg_catalog.pg_type AS ate
                               ON at.typelem != 0 AND ate.oid = at.typelem
                     JOIN pg_catalog.pg_namespace AS atn
                          ON atn.oid = at.typnamespace
                     LEFT JOIN (pg_type bt JOIN pg_namespace nbt ON bt.typnamespace = nbt.oid)
                          ON at.typtype = 'd'::"char" AND at.typbasetype = bt.oid
            WHERE cls.relkind = 'c'
              and ${filter.toFilterString(schemaField = "clsn.nspname", tableField = "cls.relname")};
        """
        ) { it.parseColumn() }.groupBy({ it.first }, { it.second })
    }

    private fun getPrimaryKeys(filter: SqlObjectFilter): Map<SqlObjectName, Table.PrimaryKey> {
        data class PrimaryKeyColumn(val keyName: String, val columnName: Column.Name, val idx: Int)

        if (filter.isEmpty()) return emptyMap()
        return connection.executeQuery(
            """
                SELECT 
                    tc.table_schema,
                    tc.table_name,
                    kcu.column_name,
                    kcu.constraint_name,
                    kcu.ordinal_position
                FROM information_schema.table_constraints AS tc
                JOIN information_schema.key_column_usage AS kcu 
                    ON tc.constraint_name = kcu.constraint_name
                        AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
                    AND ${filter.toFilterString(schemaField = "tc.table_schema", tableField = "tc.table_name")};
            """
        ) { resultSet ->
            val table = SqlObjectName(
                schema = dbName.toSchema(resultSet.getString("table_schema")!!),
                name = resultSet.getString("table_name")!!,
            )
            table to PrimaryKeyColumn(
                keyName = resultSet.getString("constraint_name")!!,
                columnName = Column.Name(resultSet.getString("column_name")!!),
                idx = resultSet.getInt("ordinal_position")
            )
        }.groupBy({ it.first }, { it.second })
            .mapValues { (table, columns) ->
                Table.PrimaryKey(
                    keyName = columns.map { it.keyName }.distinct().singleOrNull()
                        ?: error("multiple primary keys for table $table"),
                    columnNames = columns.sortedBy { it.idx }.map { it.columnName },
                )
            }
    }

    private fun getForeignKeys(filter: SqlObjectFilter): Map<SqlObjectName, List<Table.ForeignKey>> {
        data class ForeignKeyMetaData(
            val name: String,
            val sourceTable: SqlObjectName,
            val targetTable: SqlObjectName,
        )

        if (filter.isEmpty()) return emptyMap()
        return connection.executeQuery(
            """
             SELECT 
                tc.constraint_name as constraint_name,
                tc.table_schema AS source_schema,
                tc.table_name AS source_table,
                kcu.column_name AS source_column,
                ccu.table_schema AS target_schema,
                ccu.table_name AS target_table,
                ccu.column_name AS target_column
            FROM information_schema.table_constraints AS tc
            JOIN information_schema.key_column_usage AS kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage AS ccu
                ON tc.constraint_name = ccu.constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY'
                AND ${filter.toFilterString(schemaField = "tc.table_schema", tableField = "tc.table_name")}
                AND ${filter.toFilterString(schemaField = "ccu.table_schema", tableField = "ccu.table_name")};
            """
        ) { resultSet ->
            val meta = ForeignKeyMetaData(
                name = resultSet.getString("constraint_name")!!,
                sourceTable = SqlObjectName(
                    schema = dbName.toSchema(resultSet.getString("source_schema")!!),
                    name = resultSet.getString("source_table")!!,
                ),
                targetTable = SqlObjectName(
                    schema = dbName.toSchema(resultSet.getString("target_schema")!!),
                    name = resultSet.getString("target_table")!!,
                ),
            )
            val ref = Table.ForeignKey.KeyPair(
                sourceColumn = Column.Name(resultSet.getString("source_column")!!),
                targetColumn = Column.Name(resultSet.getString("target_column")!!),
            )
            meta to ref
        }
            .groupBy({ it.first }, { it.second })
            .map { (meta, refs) ->
                val key = Table.ForeignKey(
                    name = meta.name,
                    targetTable = meta.targetTable,
                    references = refs.distinct(),
                )
                meta.sourceTable to key
            }.groupBy({ it.first }, { it.second })
    }

    private fun getUniqueConstraints(filter: SqlObjectFilter): Map<SqlObjectName, List<String>> {
        if (filter.isEmpty()) return emptyMap()
        return connection.executeQuery(
            """
             SELECT
                 tc.constraint_name as constraint_name,
                 tc.table_schema AS schema,
                 tc.table_name AS table_name
             FROM information_schema.table_constraints AS tc
             WHERE tc.constraint_type = 'UNIQUE'
                AND ${filter.toFilterString(schemaField = "tc.table_schema", tableField = "tc.table_name")};
            """
        ) { resultSet ->
            val table = SqlObjectName(
                schema = dbName.toSchema(resultSet.getString("schema")!!),
                name = resultSet.getString("table_name")!!,
            )
            val name = resultSet.getString("constraint_name")!!
            table to name
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct().sorted() }
    }

    private fun getCheckConstraints(filter: SqlObjectFilter): Map<SqlObjectName, List<String>> {
        if (filter.isEmpty()) return emptyMap()
        return connection.executeQuery(
            """
             SELECT
                 tc.constraint_name as constraint_name,
                 tc.table_schema AS schema,
                 tc.table_name AS table_name
             FROM information_schema.table_constraints AS tc
             WHERE tc.constraint_type = 'CHECK'
                AND tc.constraint_name NOT LIKE '%_not_null'
                AND ${filter.toFilterString(schemaField = "tc.table_schema", tableField = "tc.table_name")};
            """
        ) { resultSet ->
            val table = SqlObjectName(
                schema = dbName.toSchema(resultSet.getString("schema")!!),
                name = resultSet.getString("table_name")!!,
            )
            val name = resultSet.getString("constraint_name")!!
            table to name
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct().sorted() }
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

    fun getEnums(enumNames: Set<SqlObjectName>): List<Enum> {
        data class EnumField(val order: UInt, val label: String)

        val filter = SqlObjectFilter.Objects(enumNames)
        if (filter.isEmpty()) return emptyList()
        val enums = connection.executeQuery(
            """
                SELECT 
                    na.nspname as enum_schema, 
                    ty.typname as enum_name, 
                    en.enumsortorder as enum_value_order, 
                    en.enumlabel as enum_value_label
                FROM pg_catalog.pg_type as ty
                JOIN pg_catalog.pg_namespace as na
                    ON ty.typnamespace = na.oid
                JOIN pg_catalog.pg_enum as en
                    ON en.enumtypid = ty.oid
                WHERE typcategory = 'E'
                    AND ${filter.toFilterString(schemaField = "na.nspname", tableField = "ty.typname")};
            """
        ) { resultSet ->
            val name = SqlObjectName(
                schema = dbName.toSchema(resultSet.getString("enum_schema")!!),
                name = resultSet.getString("enum_name"),
            )
            val field = EnumField(
                order = resultSet.getInt("enum_value_order").takeIf { it > 0 }!!.toUInt(),
                label = resultSet.getString("enum_value_label")!!,
            )
            name to field
        }
            .groupBy({ it.first }, { it.second })
            .map { (name, fields) ->
                Enum(
                    name = name,
                    fields = fields.sortedBy { it.order }.map { it.label },
                )
            }
        val missingEnums = enumNames - enums.map { it.name }.toSet()
        if (missingEnums.isNotEmpty())
            throw Exception("enums not found: $missingEnums")
        return enums
    }

    private fun ResultSet.parseColumn(): Pair<SqlObjectName, Column> {
        val tableName = SqlObjectName(
            schema = dbName.toSchema(getString("table_schema")!!),
            name = getString("table_name")!!,
        )
        val rawType = getColumnType()
        val type = run {
            Domain(
                name = SqlObjectName(
                    schema = SchemaName(
                        dbName = dbName,
                        schemaName = getString("domain_schema") ?: return@run null,
                    ),
                    name = getString("domain_name") ?: return@run null,
                ),
                originalType = rawType,
            )
        } ?: rawType
        return tableName to Column(
            pos = getInt("pos"),
            name = Column.Name(getString("column_name")!!),
            type = type,
            isNullable = getBoolean("is_nullable"),
            default = getString("column_default")
        )
    }

    override fun close() {
        connection.close()
    }

    companion object {
        private fun getPrimitiveType(name: String) =
            Primitive.entries.firstOrNull { it.sqlType == name }
                ?: error("undefined primitive type name '$name'")
    }
}