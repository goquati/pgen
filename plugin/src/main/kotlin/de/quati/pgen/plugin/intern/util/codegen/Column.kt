package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.initializer
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.sql.Column
import de.quati.pgen.plugin.intern.util.codegen.oas.DbContext


context(c: CodeGenContext)
private fun Column.getDefaultExpression(): Pair<String, List<Any>>? = when (type) {
    Column.Type.Primitive.TIMESTAMP -> when (default) {
        "now()" -> ".defaultExpression(%T)" to listOf(Poet.Exposed.defaultExpTimestamp)
        else -> null
    }

    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> when (default) {
        "now()" -> ".defaultExpression(%T)" to listOf(Poet.Exposed.defaultExpTimestampZ)
        else -> null
    }

    Column.Type.Primitive.UUID -> when (default) {
        "gen_random_uuid()" -> ".defaultExpression(%T(%S, %T()))" to
                listOf(Poet.Exposed.customFunction, "gen_random_uuid", c.poet.uuidColumnType)

        else -> null
    }

    Column.Type.Primitive.BOOL -> when (default) {
        "false" -> ".default(false)" to emptyList()
        "true" -> ".default(true)" to emptyList()
        else -> null
    }

    Column.Type.Primitive.INT4, Column.Type.Primitive.INT8 -> {
        val prefix = "nextval('"
        val suffix = "'::regclass)"
        default
            ?.takeIf { it.startsWith(prefix) && it.endsWith(suffix) }
            ?.removePrefix(prefix)?.removeSuffix(suffix)
            ?.let { seqName ->
                ".autoIncrement(%S)" to listOf(seqName)
            }
    }

    else -> null
}

context(c: CodeGenContext, _: DbContext)
internal fun PropertySpec.Builder.initializer(column: Column, postfix: String, postArgs: List<Any>) {
    val columnName = column.name.value
    var postfix = postfix
    var postArgs = postArgs.toTypedArray()
    column.getDefaultExpression()?.let {
        postfix = it.first + postfix
        postArgs = (it.second + postArgs.toList()).toTypedArray()
    }

    val type = when (val type = column.type) {
        is Column.Type.Reference -> c.getRefTypeOrThrow(type)
        else -> type
    }

    when (type) {
        is Column.Type.Reference -> error("reference type should be resolved here")
        is Column.Type.NonPrimitive.Array -> {
            fun default() = @Suppress("SpreadOperator") initializer(
                "array<%T>(name = %S)$postfix",
                type.getTypeName(), columnName, *postArgs
            )

            when (val elementType = type.elementType) {
                is Column.Type.NonPrimitive.Enum -> initializer(buildCodeBlock {
                    add("%T<%T>(\n", Poet.Pgen.pgenEnumArray, type.getTypeName())
                    add("    name = %S,\n", columnName)
                    add("    sql = %S,\n", "${elementType.name.schema.schemaName}.${elementType.name.name}")
                    @Suppress("SpreadOperator") add(")$postfix", *postArgs)
                })

                Column.Type.Primitive.UUID -> when (c.connectionType) {
                    Config.ConnectionType.JDBC -> default()
                    Config.ConnectionType.R2DBC -> initializer(buildCodeBlock {
                        add("%T(\n", Poet.Pgen.pgenUuidArray)
                        add("    name = %S,\n", columnName)
                        @Suppress("SpreadOperator") add(")$postfix", *postArgs)
                    })
                }

                is Column.Type.NonPrimitive.Composite -> @Suppress("SpreadOperator") initializer(
                    "array<%T>(name = %S, columnType = %T)$postfix",
                    type.getTypeName(), columnName, elementType.getColumnTypeTypeName(), *postArgs
                )

                else -> default()
            }
        }

        is Column.Type.NonPrimitive.Enum -> initializer(buildCodeBlock {
            add("%T<%T>(\n", Poet.Pgen.pgenEnum, type.getTypeName())
            add("    name = %S,\n", columnName)
            add("    sql = %S,\n", "${type.name.schema.schemaName}.${type.name.name}")
            @Suppress("SpreadOperator") add(")$postfix", *postArgs)
        })

        is Column.Type.NonPrimitive.Composite -> @Suppress("SpreadOperator") initializer(
            "registerColumn(name = %S, type = %T)$postfix",
            columnName, type.getColumnTypeTypeName(), *postArgs,
        )

        is Column.Type.NonPrimitive.DomainType -> @Suppress("SpreadOperator") initializer {
            add("%T<%T, %T>(\n", Poet.Pgen.domainType, type.getDomainTypename(), type.originalType.getTypeName())
            add("    name = %S,\n", columnName)
            add("    sqlType = %S,\n", type.sqlType)
            add("    originType = "); add(type.originalType.getExposedColumnType()); add(",\n")
            add(
                "    builder = { %T${type.parserFunction}(it as %T) }\n",
                type.getDomainTypename(),
                type.originalType.getTypeName(),
            )
            add(")$postfix", *postArgs)
        }

        is Column.Type.NonPrimitive.PgVector -> @Suppress("SpreadOperator") initializer(
            """
            %T(
                name = %S,
                schema = %S,
            )$postfix""".trimIndent(),
            Poet.Pgen.pgVector,
            columnName,
            type.schema,
            *postArgs
        )

        Column.Type.Primitive.INT8 -> @Suppress("SpreadOperator") initializer(
            "long(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.BOOL -> @Suppress("SpreadOperator") initializer(
            "bool(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.BINARY -> @Suppress("SpreadOperator") initializer(
            "binary(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.VARCHAR -> @Suppress("SpreadOperator") initializer(
            "text(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.DATE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Exposed.date, columnName, *postArgs
        )

        Column.Type.Primitive.INTERVAL -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.interval, columnName, *postArgs
        )

        Column.Type.Primitive.INT4RANGE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.int4Range, columnName, *postArgs
        )

        Column.Type.Primitive.INT8RANGE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.int8Range, columnName, *postArgs
        )

        Column.Type.Primitive.INT4MULTIRANGE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.int4MultiRange, columnName, *postArgs
        )

        Column.Type.Primitive.INT8MULTIRANGE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.int8MultiRange, columnName, *postArgs
        )

        Column.Type.Primitive.INT4 -> @Suppress("SpreadOperator") initializer(
            "integer(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.FLOAT4 -> @Suppress("SpreadOperator") initializer(
            "float(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.FLOAT8 -> @Suppress("SpreadOperator") initializer(
            "double(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.JSON -> @Suppress("SpreadOperator") initializer(
            "%T<%T>(name = %S, jsonConfig = %T)$postfix",
            Poet.Exposed.jsonColumn, Poet.jsonElement, columnName, Poet.json, *postArgs
        )

        Column.Type.Primitive.JSONB -> @Suppress("SpreadOperator") initializer(
            "%T<%T>(name = %S, jsonConfig = %T)$postfix",
            Poet.Exposed.jsonColumn, Poet.jsonElement, columnName, Poet.json, *postArgs
        )

        is Column.Type.NonPrimitive.Numeric -> @Suppress("SpreadOperator") initializer(
            "decimal(name = %S, precision = ${type.precision}, scale = ${type.scale})$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.INT2 -> @Suppress("SpreadOperator") initializer(
            "short(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.TEXT -> @Suppress("SpreadOperator") initializer(
            "text(name = %S)$postfix",
            columnName, *postArgs
        )

        Column.Type.Primitive.CITEXT -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Pgen.citext, columnName, *postArgs
        )

        Column.Type.Primitive.TIME -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Exposed.time, columnName, *postArgs
        )

        Column.Type.Primitive.TIMESTAMP -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Exposed.timestamp, columnName, *postArgs
        )

        Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix",
            Poet.Exposed.timestampWithTimeZone, columnName, *postArgs
        )

        Column.Type.Primitive.UUID -> @Suppress("SpreadOperator") initializer(
            "%L(name = %S)$postfix",
            c.poet.uuidColumn, columnName, *postArgs
        )

        Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> @Suppress("SpreadOperator") initializer(
            "registerColumn(name = %S, type = %T())$postfix",
            columnName, Poet.Pgen.unconstrainedNumericColumnType, *postArgs
        )

        Column.Type.Primitive.REG_CLASS -> @Suppress("SpreadOperator") initializer(
            "%T(name = %S)$postfix".trimIndent(),
            Poet.Pgen.regClassColumn,
            columnName,
            *postArgs
        )

        is Column.Type.CustomType -> @Suppress("SpreadOperator") initializer(
            "registerColumn(name = %S, type = %T())$postfix",
            columnName, type.columnType.poet, *postArgs
        )
    }
}

context(_: CodeGenContext, _: DbContext)
internal fun Column.getColumnTypeName() = when (type) {
    is Column.Type.NonPrimitive.Array -> List::class.asTypeName()
        .parameterizedBy(type.getTypeName())

    else -> type.getTypeName()
}.copy(nullable = isNullable)
