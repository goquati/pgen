package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildDataClass
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.CompositeType


context(c: CodeGenContext)
internal fun CompositeType.toTypeSpecInternal() = buildDataClass(this@toTypeSpecInternal.name.prettyName) {
    primaryConstructor {
        this@toTypeSpecInternal.columns.forEach { column ->
            val type = column.getColumnTypeName()
            addParameter(column.prettyName, type)
            addProperty(name = column.prettyName, type = type) {
                initializer(column.prettyName)
            }
        }
    }

    addType(
        buildObject("ColumnType") {
            superclass(
                Poet.Pgen.Core.Column.compositeColumnType
                    .parameterizedBy(this@toTypeSpecInternal.name.typeName)
            )
            addFunction("sqlType") {
                addModifiers(KModifier.OVERRIDE)
                returns(String::class)
                addCode("return %S", this@toTypeSpecInternal.type.sqlType)
            }
            addFunction("valueFromDB") {
                addModifiers(KModifier.OVERRIDE)
                addParameter("value", Any::class)
                returns(this@toTypeSpecInternal.name.typeName)
                addCode {
                    beginControlFlow("val fields = when (value)")
                    when (c.connectionType) {
                        Config.ConnectionType.JDBC -> add(
                            "is %T -> %T.parseFields(value.value ?: \"\")\n",
                            Poet.Jdbc.PGobject,
                            Poet.Pgen.Core.Column.pgStructField,
                        )

                        Config.ConnectionType.R2DBC -> Unit
                    }
                    add("is String -> %T.parseFields(value)\n", Poet.Pgen.Core.Column.pgStructField)
                    add(
                        "else -> error(\"Unexpected value for " +
                                $$"$${this@toTypeSpecInternal.name.prettyName}: $value\")\n"
                    )
                    endControlFlow()
                    add(
                        "if (fields.size != ${this@toTypeSpecInternal.columns.size}) error(%S)\n",
                        "unexpected number of fields"
                    )
                    add("return ${this@toTypeSpecInternal.name.prettyName} (\n")
                    this@toTypeSpecInternal.columns.sortedBy { it.pos }.forEachIndexed { idx, column ->
                        add("  %L = ", column.prettyName)
                        addPgFieldConverter(column.type)
                        add(".deserialize(fields[%L]),\n", idx)
                    }
                    add(")")
                }
            }
            addFunction("notNullValueToDB") {
                addModifiers(KModifier.OVERRIDE)
                addParameter("value", this@toTypeSpecInternal.name.typeName)
                returns(String::class)
                addCode {
                    beginControlFlow("val dataStr = buildList")
                    this@toTypeSpecInternal.columns.sortedBy { it.pos }.forEach { column ->
                        add("add(")
                        addPgFieldConverter(column.type)
                        add(".serialize(value.%L))\n", column.prettyName)
                    }
                    endControlFlow()
                    add("return dataStr.%T()", Poet.Pgen.Core.Column.pgStructFieldJoin)
                }
            }

            if (c.connectionType == Config.ConnectionType.JDBC)
                addFunction("setParameter") {
                    addModifiers(KModifier.OVERRIDE)
                    addParameter("stmt", Poet.Exposed.preparedStatementApi)
                    addParameter("index", Int::class.asTypeName())
                    addParameter("value", Any::class.asTypeName().copy(nullable = true))
                    addCode {
                        beginControlFlow("val parameterValue: %T? = value?.let", Poet.Jdbc.PGobject)
                        beginControlFlow("%T().apply", Poet.Jdbc.PGobject)
                        add("type = sqlType()\n")
                        add("this.value = value as? String\n")
                        endControlFlow()
                        endControlFlow()
                        add("super.setParameter(stmt, index, parameterValue)\n")
                    }
                }
        }
    )
}

context(c: CodeGenContext)
private fun CodeBlock.Builder.addPgFieldConverter(
    type: Column.Type,
): CodeBlock.Builder = when (val type = c.resolve(type)) {
    Column.Type.Primitive.BOOL,
    Column.Type.Primitive.DATE,
    Column.Type.Primitive.FLOAT4,
    Column.Type.Primitive.FLOAT8,
    Column.Type.Primitive.INT4RANGE,
    Column.Type.Primitive.INT8RANGE,
    Column.Type.Primitive.INT4MULTIRANGE,
    Column.Type.Primitive.INT8MULTIRANGE,
    Column.Type.Primitive.INTERVAL,
    Column.Type.Primitive.JSON,
    Column.Type.Primitive.JSONB,
    Column.Type.Primitive.TIME,
    Column.Type.Primitive.CITEXT,
    Column.Type.Primitive.TIMESTAMP,
    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE,
    Column.Type.Primitive.REG_CLASS,
    is Column.Type.NonPrimitive.Array,
    is Column.Type.NonPrimitive.PgVector,
    is Column.Type.NonPrimitive.Composite,
    is Column.Type.NonPrimitive.Domain,
    is Column.Type.Overwrite,
    is Column.Type.CustomType -> throw NotImplementedError("Unsupported composite field type ${type.sqlType}")

    is Column.Type.NonPrimitive.Enum -> add(
        "%T.Enum(%T::class)",
        Poet.Pgen.Core.Column.pgStructFieldConverter,
        type.getTypeName()
    )

    is Column.Type.NonPrimitive.Numeric -> add("%T.BigDecimal", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.INT2 -> add("%T.Small", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.INT4 -> add("%T.Int", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.INT8 -> add("%T.Long", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.TEXT -> add("%T.String", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.UUID -> add("%T.Uuid", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.VARCHAR -> add("%T.String", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> add("%T.BigDecimal", Poet.Pgen.Core.Column.pgStructFieldConverter)
    Column.Type.Primitive.BINARY -> add("%T.ByteArray", Poet.Pgen.Core.Column.pgStructFieldConverter)
}
