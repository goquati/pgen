package de.quati.pgen.plugin.model.config

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.dsl.PackageName
import de.quati.pgen.plugin.model.sql.DbName
import de.quati.pgen.plugin.model.sql.KotlinClassName
import de.quati.pgen.plugin.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.model.sql.KotlinValueClass
import de.quati.pgen.plugin.model.sql.SchemaName
import de.quati.pgen.plugin.model.sql.SqlColumnName
import de.quati.pgen.plugin.model.sql.SqlObjectName
import java.nio.file.Path
import kotlin.collections.plus
import kotlin.io.path.Path

private fun String.takeIfValidAbsoluteClazzName(size: Int? = null): String? {
    val parts = split('.')
    if (parts.any(String::isBlank)) return null
    return if (size != null)
        takeIf { parts.size == size }
    else
        takeIf { parts.size > 1 }
}

data class Config(
    val dbConfigs: List<Db>,
    val packageName: PackageName,
    val outputPath: Path,
    val specFilePath: Path,
    val connectionType: ConnectionType,
    val oasConfig: OasConfig?,
) {
    enum class ConnectionType {
        JDBC, R2DBC
    }

    data class OasConfig(
        val title: String,
        val version: String,
        val oasRootPath: Path,
        val oasCommonName: String,
        val pathPrefix: String,
        val mapper: Mapper?,
        val tables: List<Table>,
        val localConfigContext: LocalConfigContext?,
    ) {
        data class LocalConfigContext(
            val type: ClassName,
            val atMethods: Set<CRUD>,
        ) {
            class Builder {
                private var type: ClassName = ClassName("default", "ILocalConfigContext")
                private var atMethods: Set<CRUD> = CRUD.entries.toSet()

                fun type(type: String) {
                    this.type = ClassName(
                        type.substringBeforeLast('.'),
                        type.substringAfterLast('.'),
                    )
                }

                fun atMethods(vararg crud: CRUD) = apply { atMethods = crud.toSet() }
                fun build() = LocalConfigContext(
                    type = type ?: error("no local config context type defined"),
                    atMethods = atMethods.toSet(),
                )
            }
        }


        data class Table(
            val name: SqlObjectName,
            val ignoreFields: Set<String>,
            val ignoreFieldsAtCreate: Set<String>,
            val ignoreFieldsAtUpdate: Set<String>,
            val ignoreMethods: Set<CRUD>,
        ) {
            class Builder(val name: SqlObjectName) {
                private val ignoreFields: MutableSet<String> = mutableSetOf()
                private val ignoreFieldsAtCreate: MutableSet<String> = mutableSetOf()
                private val ignoreFieldsAtUpdate: MutableSet<String> = mutableSetOf()
                private val ignoreMethods: MutableSet<CRUD> = mutableSetOf()

                fun ignoreFields(vararg names: String) = apply { ignoreFields.addAll(names) }
                fun ignoreFieldsAtCreate(vararg names: String) = apply { ignoreFieldsAtCreate.addAll(names) }
                fun ignoreFieldsAtUpdate(vararg names: String) = apply { ignoreFieldsAtUpdate.addAll(names) }
                fun ignoreFieldsAtCreateAndUpdate(vararg names: String) {
                    ignoreFieldsAtCreate(*names)
                    ignoreFieldsAtUpdate(*names)
                }

                fun ignoreMethods(vararg methods: CRUD) = apply { ignoreMethods.addAll(methods) }

                fun build() = Table(
                    name = name,
                    ignoreFields = ignoreFields.toSet(),
                    ignoreFieldsAtCreate = ignoreFieldsAtCreate.toSet(),
                    ignoreFieldsAtUpdate = ignoreFieldsAtUpdate.toSet(),
                    ignoreMethods = ignoreMethods.toSet(),
                )
            }
        }

        data class Mapper(
            val packageOasModel: String,
        )

        enum class CRUD {
            CREATE, READ, READ_ALL, UPDATE, DELETE
        }

        class Builder {
            var title: String = "Backend"
            var version: String = "1.0.0"
            var oasRootPath: Path? = null
            var oasCommonName: String = "Common"
            var pathPrefix: String = "/api"
            private var localConfigContext: LocalConfigContext? = null
            private var mapper: Mapper? = null
            private val tables: MutableList<Table> = mutableListOf()
            private val defaultIgnoreFields: MutableSet<String> = mutableSetOf()
            private val defaultIgnoreFieldsAtCreate: MutableSet<String> = mutableSetOf()
            private val defaultIgnoreFieldsAtUpdate: MutableSet<String> = mutableSetOf()

            fun localConfigContext(block: LocalConfigContext.Builder.() -> Unit) {
                localConfigContext = LocalConfigContext.Builder().apply(block).build()
            }

            fun oasRootPath(path: String) = apply { oasRootPath = Path(path) }
            fun oasRootPath(path: Path) = apply { oasRootPath = path }
            fun defaultIgnoreFields(vararg names: String) = defaultIgnoreFields.addAll(names)
            fun defaultIgnoreFieldsAtCreate(vararg names: String) = defaultIgnoreFieldsAtCreate.addAll(names)
            fun defaultIgnoreFieldsAtUpdate(vararg names: String) = defaultIgnoreFieldsAtUpdate.addAll(names)
            fun defaultIgnoreFieldsAtCreateAndUpdate(vararg names: String) {
                defaultIgnoreFieldsAtCreate(*names)
                defaultIgnoreFieldsAtUpdate(*names)
            }

            fun table(sqlTable: String, block: Table.Builder.() -> Unit = {}) {
                val objName = sqlTable.tableToSqlObjectName()
                val table = Table.Builder(objName).apply(block).build()
                tables.add(table)
            }

            fun mapper(packageOasModel: String) = apply {
                mapper = Mapper(packageOasModel = packageOasModel)
            }

            fun build() = OasConfig(
                title = title,
                version = version,
                oasRootPath = oasRootPath ?: error("oas root path is not set"),
                oasCommonName = oasCommonName,
                pathPrefix = pathPrefix,
                mapper = mapper,
                tables = tables.distinctBy { it.name }.map {
                    it.copy(
                        ignoreFields = it.ignoreFields + defaultIgnoreFields,
                        ignoreFieldsAtUpdate = it.ignoreFieldsAtUpdate + defaultIgnoreFieldsAtUpdate,
                        ignoreFieldsAtCreate = it.ignoreFieldsAtCreate + defaultIgnoreFieldsAtCreate,
                    )
                },
                localConfigContext = localConfigContext,
            )

            companion object {
                private fun String.tableToSqlObjectName(): SqlObjectName {
                    val (dbName, schemaName, tableName) = this.takeIfValidAbsoluteClazzName(size = 3)
                        ?.split('.')
                        ?: throw IllegalArgumentException("illegal column name '$this', expected format <dbName>.<schema>.<table>")
                    return SqlObjectName(
                        schema = SchemaName(dbName = DbName(dbName), schemaName = schemaName),
                        name = tableName,
                    )
                }
            }
        }
    }

    data class Db(
        val dbName: DbName,
        val connectionConfig: DbConnectionConfig?,
        val tableFilter: SqlObjectFilter,
        val statementScripts: Set<Path>,
        val typeMappings: Set<TypeMapping>,
        val enumMappings: Set<EnumMapping>,
        val typeOverwrites: Set<TypeOverwrite>,
        val columnTypeMappings: Set<ColumnTypeMapping>,
        val flyway: Flyway?,
    ) {
        data class Flyway(
            val migrationDirectory: Path,
        ) {
            class Builder {
                private var migrationDirectory: Path? = null
                fun migrationDirectory(path: Path) = apply { migrationDirectory = path }
                fun migrationDirectory(path: String) = apply { migrationDirectory = Path(path) }
                fun build() = Flyway(
                    migrationDirectory = migrationDirectory ?: error("no migration file directory defined"),
                )
            }
        }

        data class DbConnectionConfig(
            val url: String,
            val user: String,
            val password: String,
        ) {
            class Builder {
                private var url: String? = null
                private var user: String? = null
                private var password: String? = null

                fun url(value: String) = apply { url = value }
                fun user(value: String) = apply { user = value }
                fun password(value: String) = apply { password = value }
                fun build() = DbConnectionConfig(
                    url = url ?: error("invalid DB connection config, url not defined"),
                    user = user ?: error("invalid DB connection config, user not defined"),
                    password = password ?: error("invalid DB connection config, password not defined"),
                )
            }
        }

        class Builder(name: String) {
            private val dbName = DbName(name.also {
                if (it.isBlank()) error("empty DB name")
            })
            private var connectionConfig: DbConnectionConfig? = null
            private var tableFilter: SqlObjectFilter? = null
            private var statementScripts: Set<Path>? = null
            private var typeMappings: Set<TypeMapping>? = null
            private var enumMappings: Set<EnumMapping>? = null
            private var typeOverwrites: Set<TypeOverwrite>? = null
            private var columnTypeMappings: Set<ColumnTypeMapping>? = null
            private var flyway: Flyway? = null

            class StatementCollectionBuilder {
                private val scripts = linkedSetOf<Path>()
                fun addScript(file: Path) = apply { scripts.add(file) }
                fun addScript(file: String) = apply { scripts.add(Path(file)) }
                fun build() = scripts.toSet()
            }

            class TypeMappingBuilder(private val dbName: DbName) {
                private val mappings = linkedSetOf<TypeMapping>()
                fun add(
                    sqlType: String,
                    clazz: String,
                    parseFunction: String? = null,
                ) {
                    val (schemaName, name) = sqlType.takeIfValidAbsoluteClazzName(size = 2)?.split('.')
                        ?: throw IllegalArgumentException("illegal sqlType '$sqlType', expected format <schema>.<name>")
                    val entity = TypeMapping(
                        sqlType = SqlObjectName(
                            schema = SchemaName(dbName = dbName, schemaName = schemaName),
                            name = name,
                        ),
                        valueClass = KotlinValueClass(
                            name = clazz.toKotlinClassName(),
                            parseFunction = parseFunction?.takeIf(String::isNotBlank),
                        ),
                    )
                    mappings.add(entity)
                }

                fun build() = mappings.toSet()
            }

            class EnumMappingBuilder(private val dbName: DbName) {
                private val enumMappings = linkedSetOf<EnumMapping>()
                fun add(
                    sqlType: String,
                    clazz: String,
                    mappings: Map<String, String> = emptyMap(),
                ) {
                    val (schemaName, name) = sqlType.takeIfValidAbsoluteClazzName(size = 2)?.split('.')
                        ?: throw IllegalArgumentException("illegal sqlType '$sqlType', expected format <schema>.<name>")
                    val entity = EnumMapping(
                        sqlType = SqlObjectName(
                            schema = SchemaName(dbName = dbName, schemaName = schemaName),
                            name = name,
                        ),
                        enumClass = KotlinEnumClass(
                            name = clazz.toKotlinClassName(),
                            mappings = mappings,
                        ),
                    )
                    enumMappings.add(entity)
                }

                fun build() = enumMappings.toSet()
            }

            class TypeOverwriteBuilder(private val dbName: DbName) {
                private val overwrites = linkedSetOf<TypeOverwrite>()
                fun add(
                    sqlColumn: String,
                    clazz: String,
                    parseFunction: String? = null,
                ) {
                    val (schemaName, tableName, columnName) = sqlColumn.takeIfValidAbsoluteClazzName(size = 3)
                        ?.split('.')
                        ?: throw IllegalArgumentException("illegal column name '$sqlColumn', expected format <schema>.<table>.<name>")
                    val entity = TypeOverwrite(
                        sqlColumn = SqlColumnName(
                            tableName = SqlObjectName(
                                schema = SchemaName(dbName = dbName, schemaName = schemaName),
                                name = tableName,
                            ),
                            name = columnName,
                        ),
                        valueClass = KotlinValueClass(
                            name = clazz.toKotlinClassName(),
                            parseFunction = parseFunction?.takeIf(String::isNotBlank),
                        ),
                    )
                    overwrites.add(entity)
                }

                fun build() = overwrites.toSet()
            }

            class ColumnTypeMappingBuilder(private val dbName: DbName) {
                private val mappings = linkedSetOf<ColumnTypeMapping>()
                fun add(
                    sqlType: String,
                    columnTypeClass: String,
                    valueClass: String,
                ) {
                    val (schemaName, name) = sqlType.takeIfValidAbsoluteClazzName(size = 2)?.split('.')
                        ?: throw IllegalArgumentException("illegal sqlType '$sqlType', expected format <schema>.<name>")
                    val entity = ColumnTypeMapping(
                        sqlType = SqlObjectName(
                            schema = SchemaName(dbName = dbName, schemaName = schemaName),
                            name = name,
                        ),
                        columnType = columnTypeClass.toKotlinClassName(),
                        value = valueClass.toKotlinClassName(),
                    )
                    mappings.add(entity)
                }

                fun build() = mappings.toSet()
            }

            fun connectionConfig(ignoreErrors: Boolean = true, block: DbConnectionConfig.Builder.() -> Unit) = apply {
                this.connectionConfig = runCatching {
                    DbConnectionConfig.Builder().apply(block).build()
                }.getOrElse {
                    if (ignoreErrors)
                        return@apply
                    throw it
                }
            }

            fun tableFilter(block: SqlObjectFilter.Builder.() -> Unit) {
                tableFilter = SqlObjectFilter.Builder(dbName = dbName).apply(block).build()
            }

            fun statements(block: StatementCollectionBuilder.() -> Unit) {
                statementScripts = StatementCollectionBuilder().apply(block).build()
            }

            fun typeMappings(block: TypeMappingBuilder.() -> Unit) {
                typeMappings = TypeMappingBuilder(dbName = dbName).apply(block).build()
            }

            fun enumMappings(block: EnumMappingBuilder.() -> Unit) {
                enumMappings = EnumMappingBuilder(dbName = dbName).apply(block).build()
            }

            fun typeOverwrites(block: TypeOverwriteBuilder.() -> Unit) {
                typeOverwrites = TypeOverwriteBuilder(dbName = dbName).apply(block).build()
            }

            fun columnTypeMappings(block: ColumnTypeMappingBuilder.() -> Unit) {
                columnTypeMappings = ColumnTypeMappingBuilder(dbName = dbName).apply(block).build()
            }

            fun flyway(block: Flyway.Builder.() -> Unit) {
                flyway = Flyway.Builder().apply(block).build()
            }

            fun build() = Db(
                dbName = dbName,
                connectionConfig = connectionConfig,
                tableFilter = tableFilter ?: error("no table filter defined for DB config '$dbName'"),
                statementScripts = statementScripts ?: emptySet(),
                typeMappings = typeMappings?.distinctBy(TypeMapping::sqlType)?.toSet() ?: emptySet(),
                enumMappings = enumMappings?.distinctBy(EnumMapping::sqlType)?.toSet() ?: emptySet(),
                typeOverwrites = typeOverwrites?.distinctBy(TypeOverwrite::sqlColumn)?.toSet() ?: emptySet(),
                columnTypeMappings = columnTypeMappings?.distinctBy(ColumnTypeMapping::sqlType)?.toSet() ?: emptySet(),
                flyway = flyway,
            )

            companion object {
                private fun String.toKotlinClassName(): KotlinClassName {
                    takeIfValidAbsoluteClazzName()
                        ?: throw IllegalArgumentException("illegal class name '$this', provide full class name with package")
                    return KotlinClassName(
                        packageName = substringBeforeLast('.'),
                        className = substringAfterLast('.'),
                    )
                }
            }
        }
    }

    open class Builder {
        private val dbConfigs: MutableList<Db> = mutableListOf()
        private var packageName: String? = null
        private var outputPath: Path? = null
        private var specFilePath: Path? = null
        private var connectionType: ConnectionType = ConnectionType.JDBC
        private var oasConfig: OasConfig? = null

        fun connectionType(type: ConnectionType) = apply { connectionType = type }
        fun packageName(name: String) = apply { packageName = name }
        fun outputPath(path: String) = apply { outputPath = Path(path) }
        fun outputPath(path: Path) = apply { outputPath = path }
        fun specFilePath(path: String) = apply { specFilePath = Path(path) }
        fun specFilePath(path: Path) = apply { specFilePath = path }
        fun addDb(name: String, block: Db.Builder.() -> Unit) {
            val db = Db.Builder(name = name).apply(block).build()
            dbConfigs.add(db)
        }

        fun oasConfig(block: OasConfig.Builder.() -> Unit) {
            oasConfig = OasConfig.Builder().apply(block).build()
        }

        fun build() = Config(
            dbConfigs = dbConfigs
                .also { dbs ->
                    val duplicateDbNames = dbs.map { it.dbName }.groupBy { it }.filterValues { it.size > 1 }.keys
                    if (duplicateDbNames.isNotEmpty())
                        error("Duplicate DB names $duplicateDbNames")
                }
                .takeIf { it.isNotEmpty() } ?: error("no DB config defined"),
            packageName = packageName?.let { PackageName(it) } ?: error("no output package defined"),
            outputPath = outputPath ?: error("no output path defined"),
            specFilePath = specFilePath ?: error("no path pgen spec file defined"),
            connectionType = connectionType,
            oasConfig = oasConfig,
        )
    }
}