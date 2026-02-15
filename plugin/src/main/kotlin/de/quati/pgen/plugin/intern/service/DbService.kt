package de.quati.pgen.plugin.intern.service

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.Enum
import de.quati.pgen.plugin.intern.model.spec.Column.Type
import de.quati.pgen.plugin.intern.model.spec.CompositeType
import de.quati.pgen.plugin.intern.model.spec.SchemaName
import de.quati.pgen.plugin.intern.model.spec.SqlObjectName
import de.quati.pgen.plugin.intern.model.spec.SqlStatementName
import de.quati.pgen.plugin.intern.model.spec.SqlType
import de.quati.pgen.plugin.intern.model.spec.Statement
import de.quati.pgen.plugin.intern.model.spec.Table
import de.quati.pgen.plugin.intern.service.ColumnData.Companion.parseColumnData
import de.quati.pgen.plugin.intern.service.TypeData.Companion.parseTypeData
import org.intellij.lang.annotations.Language
import org.postgresql.util.PGobject
import java.io.Closeable
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.use

internal class DbService(
    columnTypeMappings: Collection<Type.CustomType>,
    connectionConfig: Config.Db.Connection
) : Closeable {
    val columnTypeMappings = columnTypeMappings.associateBy { it.name }
    private val connection by lazy {
        if ("postgresql" in connectionConfig.url)
            Class.forName("org.postgresql.Driver", true, this::class.java.classLoader) // forces init
        DriverManager.getConnection(
            connectionConfig.url,
            connectionConfig.user,
            connectionConfig.password,
        )
    }

    private fun execute(@Language("sql") query: String) {
        connection.createStatement().use { statement -> statement.execute(query) }
    }

    private fun <T> executeQuery(@Language("sql") query: String, mapper: (ResultSet) -> T): List<T> {
        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use { rs ->
                buildList { while (rs.next()) add(mapper(rs)) }
            }
        }
    }

    fun getStatements(rawStatements: List<Statement.Raw>): List<Statement> {
        if (rawStatements.isEmpty()) return emptyList()
        fun Statement.Raw.preparedStmt() = "pgen_prepare_stmt_${name.lowercase()}"
        fun Statement.Raw.tempTable() = "pgen_temp_table_${name.lowercase()}"

        val statements = rawStatements.map { raw ->
            val inputTypes = runCatching {
                execute("PREPARE ${raw.preparedStmt()} AS\n${raw.preparedPsql};")
                val result = executeQuery(
                    """
                    SELECT parameter_types as types
                    FROM pg_prepared_statements
                    WHERE name = '${raw.preparedStmt()}';
                    """.trimIndent()
                ) { rs -> (rs.getArray("types").array as Array<*>).map { (it as? PGobject)?.value!! } }
                    .single().map(::getPrimitiveType)
                execute("DEALLOCATE ${raw.preparedStmt()};")
                result
            }.getOrElse { error("Failed to extract input types of statement '${raw.name}': ${it.message}") }

            if (inputTypes.size != raw.uniqueSortedVariables.size)
                error("unexpected number of input columns in statement '${raw.name}'")

            val columns = runCatching {
                execute("CREATE TEMP TABLE ${raw.tempTable()} AS\n${raw.sql};")
                val result = getColumns(SqlObjectFilter.TempTable(setOf(raw.tempTable()))).values.single()
                execute("DROP TABLE ${raw.tempTable()};")
                result
            }.getOrElse { error("Failed to extract output types of statement '${raw.name}': ${it.message}") }

            Statement(
                name = SqlStatementName(name = raw.name),
                cardinality = raw.cardinality,
                variables = raw.allVariables,
                variableTypes = raw.uniqueSortedVariables.zip(inputTypes).toMap(),
                columns = columns.map { c ->
                    if (c.name.value in raw.nonNullColumns) c.copy(nullable = false) else c
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
                uniqueConstraints = uniqueConstraints[tableName]?.sortedBy { it.name } ?: emptyList(),
                checkConstraints = checkConstraints[tableName]?.sortedBy { it.name } ?: emptyList()
            )
        }
    }

    private fun TypeData.getColumnType(
        udtNameOverride: String? = null,
        columnTypeCategoryOverride: String? = null,
    ): Type {
        val rawType = getColumnTypeInner(
            udtNameOverride = udtNameOverride,
            columnTypeCategoryOverride = columnTypeCategoryOverride,
        )
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

    private fun TypeData.getColumnTypeInner(
        udtNameOverride: String? = null,
        columnTypeCategoryOverride: String? = null,
    ): Type {
        val schema = innerTypeSchema
        val columnTypeName = udtNameOverride ?: innerTypeName
        val sqlTypeName = SqlObjectName(schema = schema, name = columnTypeName)
        columnTypeMappings[sqlTypeName]?.also { return it }

        val columnTypeCategory = columnTypeCategoryOverride ?: typeCategory!!
        if (columnTypeName.startsWith("_")) return Type.NonPrimitive.Array(
            getColumnType(
                udtNameOverride = columnTypeName.removePrefix("_"),
                columnTypeCategoryOverride = elementTypeCategory!!,
            )
        )

        if (schema != SchemaName.PgCatalog)
            return when (columnTypeCategory) {
                "E" -> Type.NonPrimitive.Enum(sqlTypeName)
                "C" -> Type.NonPrimitive.Composite(sqlTypeName)
                "U" -> {
                    when (columnTypeName) {
                        Type.NonPrimitive.PgVector.VECTOR_NAME -> Type.NonPrimitive.PgVector(schema = schema)
                        else -> Type.Reference(sqlTypeName)
                    }
                }

                "S" -> when (columnTypeName) {
                    "citext" -> Type.Primitive.CITEXT
                    else -> Type.Reference(sqlTypeName)
                }

                else -> error("Unknown column type '$columnTypeCategory' for column type '$schema:$columnTypeName'")
            }
        return when (columnTypeName) {
            "numeric" ->
                if (numericPrecision != null && numericScale != null)
                    Type.NonPrimitive.Numeric(precision = numericPrecision, scale = numericScale)
                else if (numericPrecision == null && numericScale == null)
                    Type.Primitive.UNCONSTRAINED_NUMERIC
                else
                    error("invalid numeric type, precision: $numericPrecision, scale: $numericScale")

            else -> getPrimitiveType(columnTypeName)
        }
    }

    fun getDomainTypes(filter: SqlObjectFilter): List<Type.NonPrimitive.Domain> {
        if (filter.isEmpty()) return emptyList()
        return executeQuery(
            """select 
                   d.domain_schema     as domain_schema,
                   d.domain_name       as domain_name,
                   d.udt_schema        as inner_type_schema,
                   d.udt_name          as inner_type_name,
                   d.numeric_precision as numeric_precision,
                   d.numeric_scale     as numeric_scale,
                   ty.typcategory      as type_category,
                   tye.typcategory     as element_type_category
            from information_schema.domains as d
                     join pg_catalog.pg_namespace as na
                          on d.udt_schema = na.nspname
                     join pg_catalog.pg_type as ty
                          on ty.typnamespace = na.oid and ty.typname = d.udt_name
                     left join pg_catalog.pg_type as tye
                               on ty.typelem != 0 and tye.oid = ty.typelem
            where ${filter.toFilterString(schemaField = "d.domain_schema", tableField = "d.domain_name ")};
            """
        ) { row ->
            row.parseTypeData().getColumnType().let {
                it as? Type.NonPrimitive.Domain ?: error("expected domain type, got $it")
            }
        }
    }

    private fun getColumns(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>> {
        if (filter.isEmpty()) return emptyMap()
        return executeQuery(
            """
            SELECT
                c.ordinal_position as pos,
                c.table_schema AS table_schema,
                c.table_name AS table_name,
                c.column_name AS column_name,
                c.domain_schema as domain_schema,
                c.domain_name as domain_name,
                c.is_nullable AS is_nullable,
                c.udt_schema AS inner_type_schema,
                c.udt_name AS inner_type_name,
                c.numeric_precision AS numeric_precision,
                c.numeric_scale AS numeric_scale,
                c.column_default AS column_default,
                ty.typcategory AS type_category,
                tye.typcategory AS element_type_category
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
        ) { it.parseColumnData().parseColumn() }.groupBy({ it.first }, { it.second })
    }


    private fun getCompositeTypeFields(filter: SqlObjectFilter): Map<SqlObjectName, List<Column>> {
        return executeQuery(
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
                   COALESCE(nbt.nspname, atn.nspname)::information_schema.sql_identifier AS inner_type_schema,
                   COALESCE(bt.typname, at.typname)::information_schema.sql_identifier   AS inner_type_name,
                   information_schema._pg_numeric_precision(
                           information_schema._pg_truetypid(a.*, at.*),
                           information_schema._pg_truetypmod(a.*, at.*)
                   )::information_schema.cardinal_number                                 AS numeric_precision,
                   information_schema._pg_numeric_scale(
                           information_schema._pg_truetypid(a.*, at.*),
                           information_schema._pg_truetypmod(a.*, at.*)
                   )::information_schema.cardinal_number                                 AS numeric_scale,
                   NULL                                                                  AS column_default,
                   at.typcategory                                                        AS type_category,
                   ate.typcategory                                                       AS element_type_category
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
        ) { it.parseColumnData().parseColumn() }.groupBy({ it.first }, { it.second })
    }

    private fun getPrimaryKeys(filter: SqlObjectFilter): Map<SqlObjectName, Table.PrimaryKey> {
        data class PrimaryKeyColumn(val keyName: String, val columnName: Column.Name, val idx: Int)

        if (filter.isEmpty()) return emptyMap()
        return executeQuery(
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
                schema = SchemaName(resultSet.getString("table_schema")!!),
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
                    name = columns.map { it.keyName }.distinct().singleOrNull()
                        ?: error("multiple primary keys for table $table"),
                    columns = columns.sortedBy { it.idx }.map { it.columnName },
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
        return executeQuery(
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
                    schema = SchemaName(resultSet.getString("source_schema")!!),
                    name = resultSet.getString("source_table")!!,
                ),
                targetTable = SqlObjectName(
                    schema = SchemaName(resultSet.getString("target_schema")!!),
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

    private fun getUniqueConstraints(filter: SqlObjectFilter): Map<SqlObjectName, List<Table.UniqueConstraint>> {
        if (filter.isEmpty()) return emptyMap()
        return executeQuery(
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
                schema = SchemaName(resultSet.getString("schema")!!),
                name = resultSet.getString("table_name")!!,
            )
            val name = resultSet.getString("constraint_name")!!
            table to name
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct().sorted().map { name -> Table.UniqueConstraint(name) } }
    }

    private fun getCheckConstraints(filter: SqlObjectFilter): Map<SqlObjectName, List<Table.CheckConstraint>> {
        if (filter.isEmpty()) return emptyMap()
        return executeQuery(
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
                schema = SchemaName(resultSet.getString("schema")!!),
                name = resultSet.getString("table_name")!!,
            )
            val name = resultSet.getString("constraint_name")!!
            table to name
        }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct().sorted().map { name -> Table.CheckConstraint(name) } }
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
        val enums = executeQuery(
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
                schema = SchemaName(resultSet.getString("enum_schema")!!),
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
            error("enums not found: $missingEnums")
        return enums
    }

    private fun ColumnData.parseColumn(): Pair<SqlObjectName, Column> {
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

    override fun close() {
        connection.close()
    }
}

internal fun getPrimitiveType(name: String): Type.Primitive {
    val type = SqlType(name)
    return Type.Primitive.entries.firstOrNull { it.sqlType == type }
        ?: error("undefined primitive type name '$name'")
}

private data class ColumnData(
    val pos: Int,
    val tableSchema: SchemaName,
    val tableName: String,
    val columnName: String,
    val isNullable: Boolean,
    val columnDefault: String?,
    val typeData: TypeData,
) {
    companion object {
        fun ResultSet.parseColumnData(): ColumnData {
            return ColumnData(
                pos = getInt("pos"),
                tableSchema = getString("table_schema")!!.let(::SchemaName),
                tableName = getString("table_name"),
                columnName = getString("column_name"),
                isNullable = getBoolean("is_nullable"),
                columnDefault = getString("column_default"),
                typeData = parseTypeData(),
            )
        }
    }
}

private data class TypeData(
    val domainSchema: SchemaName?,
    val domainName: String?,
    val innerTypeSchema: SchemaName,
    val innerTypeName: String,
    val numericPrecision: Int?,
    val numericScale: Int?,
    val typeCategory: String?,
    val elementTypeCategory: String?,
) {
    companion object {
        fun ResultSet.parseTypeData(): TypeData {
            return TypeData(
                domainSchema = getString("domain_schema")?.let(::SchemaName),
                domainName = getString("domain_name"),
                innerTypeSchema = getString("inner_type_schema")!!.let(::SchemaName),
                innerTypeName = getString("inner_type_name"),
                numericPrecision = getInt("numeric_precision").takeIf { !wasNull() },
                numericScale = getInt("numeric_scale").takeIf { !wasNull() },
                typeCategory = getString("type_category"),
                elementTypeCategory = getString("element_type_category"),
            )
        }
    }
}