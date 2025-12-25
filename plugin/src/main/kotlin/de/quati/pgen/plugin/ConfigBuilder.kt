package de.quati.pgen.plugin

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.intern.PackageName
import de.quati.pgen.plugin.intern.model.config.ColumnTypeMapping
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.config.Config.ConnectionType
import de.quati.pgen.plugin.intern.model.config.EnumMapping
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter.Multi
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter.Objects
import de.quati.pgen.plugin.intern.model.config.SqlObjectFilter.Schemas
import de.quati.pgen.plugin.intern.model.config.TypeMapping
import de.quati.pgen.plugin.intern.model.config.TypeOverwrite
import de.quati.pgen.plugin.intern.model.sql.DbName
import de.quati.pgen.plugin.intern.model.sql.KotlinClassName
import de.quati.pgen.plugin.intern.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.intern.model.sql.KotlinValueClass
import de.quati.pgen.plugin.intern.model.sql.SchemaName
import de.quati.pgen.plugin.intern.model.sql.SqlColumnName
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import java.nio.file.Path
import kotlin.collections.addAll
import kotlin.collections.plus
import kotlin.io.path.Path


public open class ConfigBuilder internal constructor() {
    private val dbConfigs: MutableList<Config.Db> = mutableListOf()
    private var packageName: String? = null
    private var outputPath: Path? = null
    private var specFilePath: Path? = null
    private var connectionType: ConnectionType = ConnectionType.JDBC
    private var oas: Config.Oas? = null

    public fun setConnectionTypeJdbc(): ConfigBuilder = apply { connectionType = ConnectionType.JDBC }
    public fun setConnectionTypeR2dbc(): ConfigBuilder = apply { connectionType = ConnectionType.R2DBC }
    public fun packageName(name: String): ConfigBuilder = apply { packageName = name }
    public fun outputPath(path: String): ConfigBuilder = apply { outputPath = Path(path) }
    public fun outputPath(path: Path): ConfigBuilder = apply { outputPath = path }
    public fun specFilePath(path: String): ConfigBuilder = apply { specFilePath = Path(path) }
    public fun specFilePath(path: Path): ConfigBuilder = apply { specFilePath = path }
    public fun oas(block: Oas.() -> Unit): ConfigBuilder = apply { oas = Oas().apply(block).build() }
    public fun addDb(name: String, block: Db.() -> Unit): ConfigBuilder = apply {
        val db = Db(name = name).apply(block).build()
        dbConfigs.add(db)
    }

    internal fun build() = Config(
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
        oas = oas,
    )


    public class Db internal constructor(name: String) {
        private val dbName = DbName(name.also {
            if (it.isBlank()) error("empty DB name")
        })
        private var connection: Config.Db.Connection? = null
        private var tableFilter: de.quati.pgen.plugin.intern.model.config.SqlObjectFilter? = null
        private var statementScripts: Set<Path>? = null
        private var typeMappings: Set<TypeMapping>? = null
        private var enumMappings: Set<EnumMapping>? = null
        private var typeOverwrites: Set<TypeOverwrite>? = null
        private var columnTypeMappings: Set<ColumnTypeMapping>? = null
        private var flyway: Config.Db.Flyway? = null

        public class StatementCollectionBuilder internal constructor() {
            private val scripts = linkedSetOf<Path>()
            public fun addScript(file: Path): StatementCollectionBuilder = apply { scripts.add(file) }
            public fun addScript(file: String): StatementCollectionBuilder = apply { scripts.add(Path(file)) }
            internal fun build() = scripts.toSet()
        }

        public class TypeMappingBuilder internal constructor(private val dbName: DbName) {
            private val mappings = linkedSetOf<TypeMapping>()
            public fun add(
                sqlType: String,
                clazz: String,
                parseFunction: String? = null,
            ): TypeMappingBuilder = apply {
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

            internal fun build() = mappings.toSet()
        }

        public class EnumMappingBuilder internal constructor(private val dbName: DbName) {
            private val enumMappings = linkedSetOf<EnumMapping>()
            public fun add(
                sqlType: String,
                clazz: String,
                mappings: Map<String, String> = emptyMap(),
            ): EnumMappingBuilder = apply {
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

            internal fun build() = enumMappings.toSet()
        }

        public class TypeOverwriteBuilder internal constructor(private val dbName: DbName) {
            private val overwrites = linkedSetOf<TypeOverwrite>()
            public fun add(
                sqlColumn: String,
                clazz: String,
                parseFunction: String? = null,
            ): TypeOverwriteBuilder = apply {
                val (schemaName, tableName, columnName) = sqlColumn.takeIfValidAbsoluteClazzName(size = 3)
                    ?.split('.')
                    ?: throw IllegalArgumentException(
                        "illegal column name '$sqlColumn', expected format <schema>.<table>.<name>"
                    )
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

            internal fun build() = overwrites.toSet()
        }

        public class ColumnTypeMappingBuilder internal constructor(private val dbName: DbName) {
            private val mappings = linkedSetOf<ColumnTypeMapping>()
            public fun add(
                sqlType: String,
                columnTypeClass: String,
                valueClass: String,
            ): ColumnTypeMappingBuilder = apply {
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

            internal fun build() = mappings.toSet()
        }

        public fun connection(
            block: Connection.() -> Unit,
        ): Db = apply { connection = Connection().apply(block).build() }

        public fun tableFilter(block: SqlObjectFilter.() -> Unit): Db = apply {
            tableFilter = SqlObjectFilter(dbName = dbName).apply(block).build()
        }

        public fun statements(block: StatementCollectionBuilder.() -> Unit): Db = apply {
            statementScripts = StatementCollectionBuilder().apply(block).build()
        }

        public fun typeMappings(block: TypeMappingBuilder.() -> Unit): Db = apply {
            typeMappings = TypeMappingBuilder(dbName = dbName).apply(block).build()
        }

        public fun enumMappings(block: EnumMappingBuilder.() -> Unit): Db = apply {
            enumMappings = EnumMappingBuilder(dbName = dbName).apply(block).build()
        }

        public fun typeOverwrites(block: TypeOverwriteBuilder.() -> Unit): Db = apply {
            typeOverwrites = TypeOverwriteBuilder(dbName = dbName).apply(block).build()
        }

        public fun columnTypeMappings(block: ColumnTypeMappingBuilder.() -> Unit): Db = apply {
            columnTypeMappings = ColumnTypeMappingBuilder(dbName = dbName).apply(block).build()
        }

        public fun flyway(block: Flyway.() -> Unit): Db = apply {
            flyway = Flyway().apply(block).build()
        }

        internal fun build() = Config.Db(
            dbName = dbName,
            connection = connection,
            tableFilter = tableFilter ?: error("no table filter defined for DB config '$dbName'"),
            statementScripts = statementScripts ?: emptySet(),
            typeMappings = typeMappings?.distinctBy(TypeMapping::sqlType)?.toSet() ?: emptySet(),
            enumMappings = enumMappings?.distinctBy(EnumMapping::sqlType)?.toSet() ?: emptySet(),
            typeOverwrites = typeOverwrites?.distinctBy(TypeOverwrite::sqlColumn)?.toSet() ?: emptySet(),
            columnTypeMappings = columnTypeMappings?.distinctBy(ColumnTypeMapping::sqlType)?.toSet() ?: emptySet(),
            flyway = flyway,
        )


        public class SqlObjectFilter internal constructor(
            private val dbName: DbName,
        ) {
            private val schemas: MutableSet<SchemaName> = mutableSetOf()
            private val tables: MutableSet<SqlObjectName> = mutableSetOf()

            public fun addSchema(name: String): SqlObjectFilter =
                apply { schemas.add(dbName.toSchema(name)) }

            public fun addSchemas(vararg names: String): SqlObjectFilter =
                apply { schemas.addAll(names.map { dbName.toSchema(it) }) }

            public fun addTable(schema: String, table: String): SqlObjectFilter =
                apply { tables.add(SqlObjectName(dbName.toSchema(schema), table)) }

            internal fun build(): de.quati.pgen.plugin.intern.model.config.SqlObjectFilter {
                val schemaFilter = Schemas(schemas).takeIf { it.isNotEmpty() }
                val tableFilter = Objects(tables).takeIf { it.isNotEmpty() }
                return if (schemaFilter != null && tableFilter != null)
                    Multi(listOf(schemaFilter, tableFilter))
                else
                    tableFilter ?: schemaFilter ?: error("cannot build empty sql filter")
            }
        }

        public class Flyway internal constructor() {
            private var migrationDirectory: Path? = null
            public fun migrationDirectory(path: Path): Flyway = apply { migrationDirectory = path }
            public fun migrationDirectory(path: String): Flyway = apply {
                migrationDirectory = Path(path)
            }

            internal fun build() = Config.Db.Flyway(
                migrationDirectory = migrationDirectory ?: error("no migration file directory defined"),
            )
        }

        public class Connection internal constructor() {
            private var url: String? = null
            private var user: String? = null
            private var password: String? = null

            public fun url(value: String): Connection = apply { url = value }
            public fun user(value: String): Connection = apply { user = value }
            public fun password(value: String): Connection = apply { password = value }
            internal fun build() = Config.Db.Connection(
                url = url ?: error("invalid DB connection config, url not defined"),
                user = user ?: error("invalid DB connection config, user not defined"),
                password = password ?: error("invalid DB connection config, password not defined"),
            )
        }

        private companion object {
            private fun String.toKotlinClassName(): KotlinClassName {
                takeIfValidAbsoluteClazzName()
                    ?: throw IllegalArgumentException(
                        "illegal class name '$this', provide full class name with package"
                    )
                return KotlinClassName(
                    packageName = substringBeforeLast('.'),
                    className = substringAfterLast('.'),
                )
            }
        }
    }


    public class Oas internal constructor() {
        public var title: String = "Backend"
        public var version: String = "1.0.0"
        public var oasRootPath: Path? = null
        public var oasCommonName: String = "Common"
        public var pathPrefix: String = "/api"
        private var localConfigContext: Config.Oas.LocalConfigContext? = null
        private var mapper: Config.Oas.Mapper? = null
        private val tables: MutableList<Config.Oas.Table> = mutableListOf()
        private val defaultIgnoreFields: MutableSet<String> = mutableSetOf()
        private val defaultIgnoreFieldsAtCreate: MutableSet<String> = mutableSetOf()
        private val defaultIgnoreFieldsAtUpdate: MutableSet<String> = mutableSetOf()

        public fun localConfigContext(block: LocalConfigContext.() -> Unit): Oas = apply {
            localConfigContext = LocalConfigContext().apply(block).build()
        }

        public fun oasRootPath(path: String): Oas = apply { oasRootPath = Path(path) }
        public fun oasRootPath(path: Path): Oas = apply { oasRootPath = path }
        public fun defaultIgnoreFields(vararg names: String): Oas = apply {
            defaultIgnoreFields.addAll(names)
        }

        public fun defaultIgnoreFieldsAtCreate(vararg names: String): Oas = apply {
            defaultIgnoreFieldsAtCreate.addAll(names)
        }

        public fun defaultIgnoreFieldsAtUpdate(vararg names: String): Oas = apply {
            defaultIgnoreFieldsAtUpdate.addAll(names)
        }

        public fun defaultIgnoreFieldsAtCreateAndUpdate(vararg names: String): Oas = apply {
            defaultIgnoreFieldsAtCreate(*names)
            defaultIgnoreFieldsAtUpdate(*names)
        }

        public fun table(sqlTable: String, block: Table.() -> Unit = {}) {
            val objName = sqlTable.tableToSqlObjectName()
            val table = Table(objName).apply(block).build()
            tables.add(table)
        }

        public fun mapper(packageOasModel: String): Oas = apply {
            mapper = Config.Oas.Mapper(packageOasModel = packageOasModel)
        }

        internal fun build() = Config.Oas(
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

        public class Table internal constructor(private val name: SqlObjectName) {
            private val ignoreFields: MutableSet<String> = mutableSetOf()
            private val ignoreFieldsAtCreate: MutableSet<String> = mutableSetOf()
            private val ignoreFieldsAtUpdate: MutableSet<String> = mutableSetOf()
            private val ignoreMethods: MutableSet<CRUD> = mutableSetOf()

            public fun ignoreFields(vararg names: String): Table = apply { ignoreFields.addAll(names) }
            public fun ignoreFieldsAtCreate(vararg names: String): Table = apply {
                ignoreFieldsAtCreate.addAll(names)
            }

            public fun ignoreFieldsAtUpdate(vararg names: String): Table = apply {
                ignoreFieldsAtUpdate.addAll(names)
            }

            public fun ignoreFieldsAtCreateAndUpdate(vararg names: String): Table = apply {
                ignoreFieldsAtCreate(*names)
                ignoreFieldsAtUpdate(*names)
            }

            public fun ignoreMethods(vararg methods: CRUD): Table = apply { ignoreMethods.addAll(methods) }

            internal fun build() = Config.Oas.Table(
                name = name,
                ignoreFields = ignoreFields.toSet(),
                ignoreFieldsAtCreate = ignoreFieldsAtCreate.toSet(),
                ignoreFieldsAtUpdate = ignoreFieldsAtUpdate.toSet(),
                ignoreMethods = ignoreMethods.toSet(),
            )
        }

        public class LocalConfigContext internal constructor() {
            private var type: ClassName = ClassName("default", "ILocalConfigContext")
            private var atMethods: Set<CRUD> = CRUD.entries.toSet()

            public fun type(type: String): LocalConfigContext = apply {
                this.type = ClassName(
                    type.substringBeforeLast('.'),
                    type.substringAfterLast('.'),
                )
            }

            public fun atMethods(vararg crud: CRUD): LocalConfigContext = apply { atMethods = crud.toSet() }
            internal fun build() = Config.Oas.LocalConfigContext(
                type = type,
                atMethods = atMethods.toSet(),
            )
        }

        private companion object {
            private fun String.tableToSqlObjectName(): SqlObjectName {
                val (dbName, schemaName, tableName) = takeIfValidAbsoluteClazzName(size = 3)
                    ?.split('.')
                    ?: throw IllegalArgumentException(
                        "illegal column name '$this', expected format <dbName>.<schema>.<table>"
                    )
                return SqlObjectName(
                    schema = SchemaName(dbName = DbName(dbName), schemaName = schemaName),
                    name = tableName,
                )
            }
        }
    }

    private companion object {
        private fun String.takeIfValidAbsoluteClazzName(size: Int? = null): String? {
            val parts = split('.')
            if (parts.any(String::isBlank)) return null
            return if (size != null)
                takeIf { parts.size == size }
            else
                takeIf { parts.size > 1 }
        }
    }
}