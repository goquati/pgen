package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.spec.Column


context(c: CodeGenContext)
internal fun Column.getDefaultExpression(): CodeBlock? = when (type) {
    Column.Type.Primitive.TIMESTAMP -> when (defaultExpr) {
        "now()" -> CodeBlock.of(".defaultExpression(%T)", Poet.Exposed.defaultExpTimestamp)
        else -> null
    }

    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> when (defaultExpr) {
        "now()" -> CodeBlock.of(".defaultExpression(%T)", Poet.Exposed.defaultExpTimestampZ)
        else -> null
    }

    Column.Type.Primitive.UUID -> when (defaultExpr) {
        "gen_random_uuid()" -> CodeBlock.of(
            ".defaultExpression(%T(%S, %T()))",
            Poet.Exposed.customFunction, "gen_random_uuid", c.poet.uuidColumnType,
        )

        else -> null
    }

    Column.Type.Primitive.BOOL -> when (defaultExpr) {
        "false" -> CodeBlock.of(".default(false)")
        "true" -> CodeBlock.of(".default(true)")
        else -> null
    }

    Column.Type.Primitive.INT4, Column.Type.Primitive.INT8 -> {
        val prefix = "nextval('"
        val suffix = "'::regclass)"
        defaultExpr
            ?.takeIf { it.startsWith(prefix) && it.endsWith(suffix) }
            ?.removePrefix(prefix)?.removeSuffix(suffix)
            ?.let { seqName ->
                CodeBlock.of(".autoIncrement(%S)", seqName)
            }
    }

    else -> null
}

context(c: CodeGenContext)
internal fun initializerBlock(column: Column): CodeBlock {
    val columnName = column.name.value
    return when (val type = c.resolve(column.type)) {
        is Column.Type.NonPrimitive.Array -> {
            fun default() = CodeBlock.of("array<%T>(name = %S)", type.getTypeName(), columnName)

            when (val elementType = c.resolve(type.element)) {
                is Column.Type.NonPrimitive.Enum -> buildCodeBlock {
                    add("%T<%T>(\n", Poet.Pgen.pgenEnumArray, type.getTypeName())
                    add("    name = %S,\n", columnName)
                    add("    sql = %S,\n", "${elementType.ref}")
                    add(")")
                }

                Column.Type.Primitive.UUID -> when (c.connectionType) {
                    Config.ConnectionType.JDBC -> default()
                    Config.ConnectionType.R2DBC -> buildCodeBlock {
                        add("%T(\n", Poet.Pgen.pgenUuidArray)
                        add("    name = %S,\n", columnName)
                        add(")")
                    }
                }

                is Column.Type.NonPrimitive.Composite -> CodeBlock.of(
                    "array<%T>(name = %S, columnType = %T)",
                    type.getTypeName(), columnName, elementType.getColumnTypeTypeName()
                )

                is Column.Type.DomainType -> when (c.connectionType) {
                    Config.ConnectionType.JDBC -> buildCodeBlock {
                        add("array(\n")
                        add("    name = %S,\n", columnName)
                        add("    columnType = %T.create(\n", Poet.Pgen.domainColumnType)
                        add("        sqlType = %S,\n", elementType.sqlType)
                        add("        originType = "); add(elementType.base.getExposedColumnType()); add(",\n")
                        add(
                            "        builder = { %T${elementType.parserFunction}(it as %T) },\n",
                            elementType.getDomainTypename(),
                            elementType.base.getTypeName(),
                        )
                        add("    ),\n", Poet.Pgen.domainTypeColumn)
                        add(")")
                    }

                    Config.ConnectionType.R2DBC -> buildCodeBlock {
                        add("%T(\n", Poet.Pgen.pgenDomainArray)
                        add("    name = %S,\n", columnName)
                        add("    sqlType = %S,\n", elementType.sqlType)
                        add("    originType = "); add(elementType.base.getExposedColumnType()); add(",\n")
                        add(
                            "    builder = { %T${elementType.parserFunction}(it as %T) },\n",
                            elementType.getDomainTypename(),
                            elementType.base.getTypeName(),
                        )
                        add(")")
                    }
                }

                else -> default()
            }
        }

        is Column.Type.NonPrimitive.Enum -> buildCodeBlock {
            add("%T<%T>(\n", Poet.Pgen.pgenEnum, type.getTypeName())
            add("    name = %S,\n", columnName)
            add("    sql = %S,\n", "${type.ref}")
            add(")")
        }

        is Column.Type.NonPrimitive.Composite -> CodeBlock.of(
            "registerColumn(name = %S, type = %T)",
            columnName, type.getColumnTypeTypeName(),
        )

        is Column.Type.DomainType -> buildCodeBlock {
            add("%T<%T, %T>(\n", Poet.Pgen.domainType, type.getDomainTypename(), type.base.getTypeName())
            add("    name = %S,\n", columnName)
            add("    sqlType = %S,\n", type.sqlType)
            add("    originType = "); add(type.base.getExposedColumnType()); add(",\n")
            add(
                "    builder = { %T${type.parserFunction}(it as %T) }\n",
                type.getDomainTypename(),
                type.base.getTypeName(),
            )
            add(")")
        }

        is Column.Type.NonPrimitive.PgVector -> CodeBlock.of(
            """
            %T(
                name = %S,
                schema = %S,
            )""".trimIndent(),
            Poet.Pgen.pgVector,
            columnName,
            type.schema
        )

        Column.Type.Primitive.INT8 -> CodeBlock.of("long(name = %S)", columnName)
        Column.Type.Primitive.BOOL -> CodeBlock.of("bool(name = %S)", columnName)
        Column.Type.Primitive.BINARY -> CodeBlock.of("binary(name = %S)", columnName)
        Column.Type.Primitive.VARCHAR -> CodeBlock.of("text(name = %S)", columnName)
        Column.Type.Primitive.DATE -> CodeBlock.of("%T(name = %S)", Poet.Exposed.date, columnName)
        Column.Type.Primitive.INTERVAL -> CodeBlock.of("%T(name = %S)", Poet.Pgen.interval, columnName)
        Column.Type.Primitive.INT4RANGE -> CodeBlock.of("%T(name = %S)", Poet.Pgen.int4Range, columnName)
        Column.Type.Primitive.INT8RANGE -> CodeBlock.of("%T(name = %S)", Poet.Pgen.int8Range, columnName)
        Column.Type.Primitive.INT4MULTIRANGE -> CodeBlock.of("%T(name = %S)", Poet.Pgen.int4MultiRange, columnName)
        Column.Type.Primitive.INT8MULTIRANGE -> CodeBlock.of("%T(name = %S)", Poet.Pgen.int8MultiRange, columnName)
        Column.Type.Primitive.INT4 -> CodeBlock.of("integer(name = %S)", columnName)
        Column.Type.Primitive.FLOAT4 -> CodeBlock.of("float(name = %S)", columnName)
        Column.Type.Primitive.FLOAT8 -> CodeBlock.of("double(name = %S)", columnName)
        Column.Type.Primitive.INT2 -> CodeBlock.of("short(name = %S)", columnName)
        Column.Type.Primitive.TEXT -> CodeBlock.of("text(name = %S)", columnName)
        Column.Type.Primitive.CITEXT -> CodeBlock.of("%T(name = %S)", Poet.Pgen.citext, columnName)
        Column.Type.Primitive.TIME -> CodeBlock.of("%T(name = %S)", Poet.Exposed.time, columnName)
        Column.Type.Primitive.TIMESTAMP -> CodeBlock.of("%T(name = %S)", Poet.Exposed.timestamp, columnName)
        Column.Type.Primitive.UUID -> CodeBlock.of("%L(name = %S)", c.poet.uuidColumn, columnName)

        Column.Type.Primitive.JSON -> CodeBlock.of(
            "%T<%T>(name = %S, jsonConfig = %T)",
            Poet.Exposed.jsonColumn, Poet.jsonElement, columnName, Poet.json
        )

        Column.Type.Primitive.JSONB -> CodeBlock.of(
            "%T<%T>(name = %S, jsonConfig = %T)",
            Poet.Exposed.jsonColumn, Poet.jsonElement, columnName, Poet.json
        )

        Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> CodeBlock.of(
            "%T(name = %S)",
            Poet.Exposed.timestampWithTimeZone, columnName
        )

        Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> CodeBlock.of(
            "registerColumn(name = %S, type = %T())",
            columnName, Poet.Pgen.unconstrainedNumericColumnType
        )

        Column.Type.Primitive.REG_CLASS -> CodeBlock.of(
            "%T(name = %S)".trimIndent(),
            Poet.Pgen.regClassColumn,
            columnName,
        )

        is Column.Type.NonPrimitive.Numeric -> CodeBlock.of(
            "decimal(name = %S, precision = ${type.precision}, scale = ${type.scale})",
            columnName
        )

        is Column.Type.CustomType -> CodeBlock.of(
            "registerColumn(name = %S, type = %T())",
            columnName,
            type.columnType.poet
        )
    }
}

context(_: CodeGenContext)
internal fun Column.getColumnTypeName() = when (type) {
    is Column.Type.NonPrimitive.Array -> List::class.asTypeName()
        .parameterizedBy(type.getTypeName())

    else -> type.getTypeName()
}.copy(nullable = nullable)
