package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.dsl.PackageName
import de.quati.pgen.plugin.dsl.fileSpec
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.oas.EnumOasData
import de.quati.pgen.plugin.model.oas.TableOasData
import de.quati.pgen.plugin.model.sql.Enum
import de.quati.pgen.plugin.model.sql.SqlObject
import de.quati.pgen.plugin.model.sql.Statement
import de.quati.pgen.plugin.model.sql.Table
import de.quati.pgen.plugin.model.sql.Column.Type.NonPrimitive.Domain
import de.quati.pgen.plugin.model.sql.CompositeType
import de.quati.pgen.plugin.service.DirectorySyncService
import de.quati.pgen.plugin.util.codegen.oas.addEnumMapper
import de.quati.pgen.plugin.util.codegen.oas.addTableMapper
import de.quati.pgen.plugin.util.codegen.oas.addTableService
import java.time.OffsetDateTime

object Poet {
    val json = ClassName("kotlinx.serialization.json", "Json")
    val jsonElement = ClassName("kotlinx.serialization.json", "JsonElement")

    val duration = ClassName("kotlin.time", "Duration")
    val instant = ClassName("kotlin.time", "Instant")
    val localTime = ClassName("kotlinx.datetime", "LocalTime")
    val localDate = ClassName("kotlinx.datetime", "LocalDate")
    val offsetDateTime = OffsetDateTime::class.asTypeName()

    val flowSingle = ClassName("kotlinx.coroutines.flow", "single")
    val flow = ClassName("kotlinx.coroutines.flow", "Flow")
    val generateChannelFlow = ClassName("kotlinx.coroutines.flow", "channelFlow")
    val trySendBlocking = ClassName("kotlinx.coroutines.channels", "trySendBlocking")

    val PGobject = ClassName("org.postgresql.util", "PGobject")

    val codecRegistrar = ClassName("io.r2dbc.postgresql.extension", "CodecRegistrar")
    val flowSingleOrNull = ClassName("kotlinx.coroutines.flow", "singleOrNull")
    val channelFlow = ClassName("kotlinx.coroutines.flow", "channelFlow")
    val flowMap = ClassName("kotlinx.coroutines.flow", "map")

    object Exposed {

        private val packageName = PackageName("org.jetbrains.exposed.v1")
        private val packageNameCore = packageName.plus("core")
        private val packageNameJson = packageName.plus("json")
        private val packageNameDatetime = packageName.plus("datetime")

        context(c: CodeGenContext)
        private fun getDbPackage() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageName.plus("jdbc")
            Config.ConnectionType.R2DBC -> packageName.plus("r2dbc")
        }

        val date = packageNameDatetime.className("date")
        val time = packageNameDatetime.className("time")
        val durationColumn = packageNameDatetime.className("duration")
        val timestamp = packageNameDatetime.className("timestamp")
        val timestampWithTimeZone = packageNameDatetime.className("timestampWithTimeZone")
        val defaultExpTimestamp = packageNameDatetime.className("CurrentTimestamp")
        val defaultExpTimestampZ = packageNameDatetime.className("CurrentTimestampWithTimeZone")
        val kotlinLocalDateColumnType = packageNameDatetime.className("KotlinLocalDateColumnType")
        val kotlinDurationColumnType = packageNameDatetime.className("KotlinDurationColumnType")
        val kotlinLocalTimeColumnType = packageNameDatetime.className("KotlinLocalTimeColumnType")
        val kotlinInstantColumnType = packageNameDatetime.className("KotlinInstantColumnType")
        val kotlinOffsetDateTimeColumnType = packageNameDatetime.className("KotlinOffsetDateTimeColumnType")

        val customFunction = packageNameCore.className("CustomFunction")
        val table = packageNameCore.className("Table")
        val transaction = packageNameCore.className("Transaction")
        val columnType = packageNameCore.className("ColumnType")
        val primaryKey = packageNameCore.className("Table", "PrimaryKey")
        val column = packageNameCore.className("Column")
        val alias = packageNameCore.className("Alias")
        val resultRow = packageNameCore.className("ResultRow")
        val uuidColumnType = packageNameCore.className("UUIDColumnType")
        val enumerationColumnType = packageNameCore.className("EnumerationColumnType")
        val decimalColumnType = packageNameCore.className("DecimalColumnType")
        val longColumnType = packageNameCore.className("LongColumnType")
        val booleanColumnType = packageNameCore.className("BooleanColumnType")
        val binaryColumnType = packageNameCore.className("BinaryColumnType")
        val textColumnType = packageNameCore.className("TextColumnType")
        val integerColumnType = packageNameCore.className("IntegerColumnType")
        val floatColumnType = packageNameCore.className("FloatColumnType")
        val doubleColumnType = packageNameCore.className("DoubleColumnType")
        val shortColumnType = packageNameCore.className("ShortColumnType")
        val eq = packageNameCore.className("eq")
        val and = packageNameCore.className("and")
        val sqlExpressionBuilder = packageNameCore.className("SqlExpressionBuilder")
        val opBoolean = packageNameCore.className("Op").parameterizedBy(Boolean::class.asTypeName())
        val fieldSet = packageNameCore.className("FieldSet")

        val jsonColumn = packageNameJson.className("json")

        context(c: CodeGenContext)
        fun updateReturning() = getDbPackage().className("updateReturning")

        context(c: CodeGenContext)
        fun insertReturning() = getDbPackage().className("insertReturning")

        context(c: CodeGenContext)
        fun selectAll() = getDbPackage().className("selectAll")

        context(c: CodeGenContext)
        fun deleteWhere() = getDbPackage().className("deleteWhere")

        context(c: CodeGenContext)
        fun query() = getDbPackage().className("Query")

        context(c: CodeGenContext)
        fun transactionFun() = getDbPackage().className(
            "transactions",
            when (c.connectionType) {
                Config.ConnectionType.JDBC -> "transaction"
                Config.ConnectionType.R2DBC -> "suspendTransaction"
            }
        )

        context(c: CodeGenContext)
        fun database() = getDbPackage().className(
            when (c.connectionType) {
                Config.ConnectionType.JDBC -> "Database"
                Config.ConnectionType.R2DBC -> "R2dbcDatabase"
            }
        )
    }

    object QuatiUtil {
        private val packageName = PackageName("de.quati.kotlin.util")
        val option = packageName.className("Option")
        val optionTakeSome = packageName.className("takeSome")
        val optionMap = packageName.className("map")
        val exception = packageName.className("QuatiException")
    }

    object Pgen {
        private val packageNameShared = PackageName("de.quati.pgen.shared")
        private val packageNameCore = PackageName("de.quati.pgen.core")
        private val packageNameCoreUtil = PackageName("de.quati.pgen.core.util")
        private val packageNameCoreColumnType = PackageName("de.quati.pgen.core.column")
        private val packageNameJdbc = PackageName("de.quati.pgen.jdbc")
        private val packageNameJdbcColumnType = PackageName("de.quati.pgen.jdbc.column")
        private val packageNameJdbcUtil = PackageName("de.quati.pgen.jdbc.util")
        private val packageNameR2dbc = PackageName("de.quati.pgen.r2dbc")
        private val packageNameR2dbcColumnType = PackageName("de.quati.pgen.r2dbc.column")
        private val packageNameR2dbcUtil = PackageName("de.quati.pgen.r2dbc.util")

        val stringLike = packageNameShared.className("StringLike")
        val regClass = packageNameShared.className("RegClass")

        val columnValueSet = packageNameCore.className("ColumnValueSet")
        val columnValue = packageNameCore.className("ColumnValue")

        val regClassColumn = packageNameCoreColumnType.className("regClass")
        val regClassColumnType = packageNameCoreColumnType.className("RegClassColumnType")

        val domainType = packageNameCoreColumnType.className("domainType")
        val domainTypeColumn = packageNameCoreColumnType.className("DomainTypeColumn")

        val getArrayColumnType = packageNameCoreColumnType.className("getArrayColumnType")
        val customEnumerationArray = packageNameCoreColumnType.className("customEnumerationArray")

        val defaultJsonColumnType = packageNameCoreColumnType.className("DefaultJsonColumnType")
        val unconstrainedNumericColumnType = packageNameCoreColumnType.className("UnconstrainedNumericColumnType")

        val pgStructFieldConverter = packageNameCoreColumnType.className("PgStructFieldConverter")
        val pgStructField = packageNameCoreColumnType.className("PgStructField")
        val pgStructFieldJoin = packageNameCoreColumnType.className("join")

        val pgEnum = packageNameCoreColumnType.className("PgEnum")
        val getPgEnumByLabel = packageNameCoreColumnType.className("getPgEnumByLabel")

        val fKeyConstraint = packageNameCore.className("Constraint", "ForeignKey")
        val pKeyConstraint = packageNameCore.className("Constraint", "PrimaryKey")
        val uniqueConstraint = packageNameCore.className("Constraint", "Unique")
        val checkConstraint = packageNameCore.className("Constraint", "Check")
        val notNullConstraint = packageNameCore.className("Constraint", "NotNull")

        context(c: CodeGenContext)
        fun pgVector() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("pgVector")
            Config.ConnectionType.R2DBC -> packageNameR2dbcColumnType.className("pgVector")
        }

        context(c: CodeGenContext)
        fun pgVectorColumnType() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("PgVectorColumnType")
            Config.ConnectionType.R2DBC -> packageNameR2dbcColumnType.className("PgVectorColumnType")
        }

        context(c: CodeGenContext)
        fun multiRange() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("MultiRange")
            Config.ConnectionType.R2DBC -> throw NotImplementedError("MultiRange")
        }

        context(c: CodeGenContext)
        fun int4RangeColumnType() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("Int4RangeColumnType")
            Config.ConnectionType.R2DBC -> throw NotImplementedError("Int4RangeColumnType")
        }

        context(c: CodeGenContext)
        fun int8RangeColumnType() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("Int8RangeColumnType")
            Config.ConnectionType.R2DBC -> throw NotImplementedError("Int8RangeColumnType")
        }

        context(c: CodeGenContext)
        fun int4MultiRangeColumnType() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("Int4MultiRangeColumnType")
            Config.ConnectionType.R2DBC -> throw NotImplementedError("Int4MultiRangeColumnType")
        }

        context(c: CodeGenContext)
        fun int8MultiRangeColumnType() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("Int8MultiRangeColumnType")
            Config.ConnectionType.R2DBC -> throw NotImplementedError("Int8MultiRangeColumnType")
        }

        val getColumnWithAlias = packageNameCoreUtil.className("get")

        context(c: CodeGenContext)
        fun toDbObject() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> packageNameJdbcColumnType.className("toDbObject")
            Config.ConnectionType.R2DBC -> packageNameR2dbcColumnType.className("toDbObject")
        }

        context(c: CodeGenContext)
        fun setLocalConfig() = when (c.connectionType) {
            Config.ConnectionType.JDBC -> throw NotImplementedError("setLocalConfig")
            Config.ConnectionType.R2DBC -> packageNameR2dbcUtil.className("setLocalConfig")
        }

    }

}

context(c: CodeGenContext)
private fun SqlObject.toTypeSpec() = when (this) {
    is Enum -> toTypeSpecInternal()
    is Table -> toTypeSpecInternal()
    is Domain -> toTypeSpecInternal()
    is CompositeType -> toTypeSpecInternal()
}

context(c: CodeGenContext)
fun FileSpec.Builder.add(obj: SqlObject) {
    val spec = obj.toTypeSpec()
    addType(spec)
}

context(c: CodeGenContext)
fun DirectorySyncService.sync(
    obj: SqlObject,
    block: FileSpec.Builder.() -> Unit = {},
) {
    val fileName = "${obj.name.prettyName}.kt"
    sync(
        relativePath = obj.name.packageName.toRelativePath(fileName),
        content = fileSpec(
            packageName = obj.name.packageName,
            name = fileName,
            block = {
                add(obj)
                block()
            }
        )
    )
}

context(c: CodeGenContext)
fun DirectorySyncService.syncQueries(allTables: Collection<Table>) {
    val fileName = "Queries.kt"
    allTables.groupBy { it.name.schema.dbName }.forEach { (dbName, tables) ->
        sync(
            relativePath = dbName.packageName.toRelativePath(fileName),
            content = fileSpec(
                packageName = dbName.packageName,
                name = fileName,
                block = {
                    tables.flatMap { it.toQueryFunctions() }.forEach {
                        addFunction(it)
                    }
                }
            )
        )
    }
}

context(c: CodeGenContext)
fun DirectorySyncService.sync(
    obj: Collection<Statement>,
    block: FileSpec.Builder.() -> Unit = {},
) {
    val fileName = "Statements.kt"
    val packageName = obj.packageName
    sync(
        relativePath = packageName.toRelativePath(fileName),
        content = fileSpec(
            packageName = packageName,
            name = fileName,
            block = {
                addStatements(obj)
                block()
            }
        )
    )
}

context(c: CodeGenContext, mapperConfig: Config.OasConfig.Mapper)
fun DirectorySyncService.sync(
    obj: EnumOasData,
    block: FileSpec.Builder.() -> Unit = {},
) {
    val fileName = "${obj.nameCapitalized}.kt"
    val packageName = c.poet.packageMapper
    sync(
        relativePath = packageName.toRelativePath(fileName),
        content = fileSpec(
            packageName = packageName,
            name = fileName,
            block = {
                addEnumMapper(obj)
                block()
            }
        )
    )
}

context(c: CodeGenContext, mapperConfig: Config.OasConfig.Mapper)
fun DirectorySyncService.sync(
    obj: TableOasData,
    block: FileSpec.Builder.() -> Unit = {},
) {
    run {
        val fileName = "${obj.nameCapitalized}.kt"
        val packageName = c.poet.packageMapper
        sync(
            relativePath = packageName.toRelativePath(fileName),
            content = fileSpec(
                packageName = packageName,
                name = fileName,
                block = {
                    addTableMapper(obj)
                    block()
                }
            )
        )
    }
    if (c.connectionType == Config.ConnectionType.R2DBC) {
        val fileName = obj.getOasServiceName() + ".kt"
        val packageName = c.poet.packageService
        sync(
            relativePath = packageName.toRelativePath(fileName),
            content = fileSpec(
                packageName = packageName,
                name = fileName,
                block = {
                    addTableService(obj)
                    block()
                }
            )
        )
    }
}

context(c: CodeGenContext)
fun DirectorySyncService.syncCodecs(
    objs: Collection<Enum>,
) {
    if (c.connectionType != Config.ConnectionType.R2DBC) return
    val fileName = "R2dbcCodecs.kt"
    val packageName = c.poet.packageDb
    sync(
        relativePath = packageName.toRelativePath(fileName),
        content = fileSpec(
            packageName = packageName,
            name = fileName,
            block = { createCodecCollection(objs) }
        )
    )
}

context(c: CodeGenContext)
fun PackageName.toRelativePath() = name.removePrefix(c.poet.rootPackageName.name)
    .trimStart('.').replace(".", "/")

context(c: CodeGenContext)
fun PackageName.toRelativePath(filename: String) = toRelativePath() + "/$filename"
