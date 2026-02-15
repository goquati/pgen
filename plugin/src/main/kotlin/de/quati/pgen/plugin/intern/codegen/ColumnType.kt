package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.intern.model.spec.Column
import java.math.BigDecimal


context(c: CodeGenContext)
internal fun Column.Type.getTypeName(innerArrayType: Boolean = true): TypeName = when (val type = c.resolve(this)) {
    is Column.Type.NonPrimitive.Array -> if (innerArrayType)
        type.element.getTypeName()
    else
        List::class.asTypeName().parameterizedBy(type.element.getTypeName())

    is Column.Type.NonPrimitive.Domain -> type.getDomainTypename()
    is Column.Type.Overwrite -> type.getValueClass().name.poet
    is Column.Type.NonPrimitive.Enum -> type.ref.typeName
    is Column.Type.NonPrimitive.PgVector -> FloatArray::class.asTypeName()
    is Column.Type.NonPrimitive.Composite -> type.ref.typeName
    is Column.Type.NonPrimitive.Numeric -> BigDecimal::class.asTypeName()
    Column.Type.Primitive.INT8 -> Long::class.asTypeName()
    Column.Type.Primitive.BOOL -> Boolean::class.asTypeName()
    Column.Type.Primitive.BINARY -> ByteArray::class.asTypeName()
    Column.Type.Primitive.VARCHAR -> String::class.asTypeName()
    Column.Type.Primitive.DATE -> Poet.localDate
    Column.Type.Primitive.INTERVAL -> Poet.dateTimePeriod
    Column.Type.Primitive.INT4RANGE -> IntRange::class.asTypeName()
    Column.Type.Primitive.INT8RANGE -> LongRange::class.asTypeName()
    Column.Type.Primitive.INT4MULTIRANGE -> Poet.Pgen.Shared.multiRange.parameterizedBy(Int::class.asTypeName())
    Column.Type.Primitive.INT8MULTIRANGE -> Poet.Pgen.Shared.multiRange.parameterizedBy(Long::class.asTypeName())
    Column.Type.Primitive.INT4 -> Int::class.asTypeName()
    Column.Type.Primitive.FLOAT4 -> Float::class.asTypeName()
    Column.Type.Primitive.FLOAT8 -> Double::class.asTypeName()
    Column.Type.Primitive.JSON -> Poet.jsonElement
    Column.Type.Primitive.JSONB -> Poet.jsonElement
    Column.Type.Primitive.INT2 -> Short::class.asTypeName()
    Column.Type.Primitive.TEXT -> String::class.asTypeName()
    Column.Type.Primitive.CITEXT -> String::class.asTypeName()
    Column.Type.Primitive.TIME -> Poet.localTime
    Column.Type.Primitive.TIMESTAMP -> Poet.instant
    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> Poet.offsetDateTime
    Column.Type.Primitive.UUID -> c.poet.uuid
    Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> BigDecimal::class.asTypeName()
    Column.Type.Primitive.REG_CLASS -> Poet.Pgen.Shared.regClass
    is Column.Type.CustomType -> type.value.poet
}

private fun codeBlock(format: String, vararg args: Any) = CodeBlock.builder().add(format, *args).build()

context(c: CodeGenContext)
internal fun Column.Type.getExposedColumnType(): CodeBlock = when (val type = c.resolve(this)) {
    is Column.Type.NonPrimitive.Array ->
        codeBlock("%T(%L)", Poet.Pgen.getArrayColumnType, type.element.getExposedColumnType())

    is Column.Type.NonPrimitive.PgVector ->
        codeBlock("%T(schema=%S)", Poet.Pgen.pgVectorColumnType, type.schema)

    is Column.Type.NonPrimitive.Composite ->
        codeBlock("%T(sqlType=%S)", type.getColumnTypeTypeName(), sqlType)

    is Column.Type.NonPrimitive.Enum ->
        codeBlock(
            "%T(name=%S, sql=%s)",
            Poet.Pgen.pgenEnumColumnType,
            type.ref.name,
            sqlType
        )

    is Column.Type.NonPrimitive.Numeric ->
        codeBlock("%T(precision = ${type.precision}, scale = ${type.scale})", Poet.Exposed.decimalColumnType)

    is Column.Type.NonPrimitive.Domain ->
        codeBlock("%T(kClass=%T::class, sqlType=%S)", Poet.Pgen.domainTypeColumn, type.getDomainTypename(), sqlType)

    is Column.Type.Overwrite ->
        codeBlock(
            "%T(kClass=%T::class, sqlType=%S)",
            Poet.Pgen.domainTypeColumn,
            type.getValueClass().name.poet,
            type.base.sqlType
        )

    Column.Type.Primitive.INT8 -> codeBlock("%T()", Poet.Exposed.longColumnType)
    Column.Type.Primitive.BOOL -> codeBlock("%T()", Poet.Exposed.booleanColumnType)
    Column.Type.Primitive.BINARY -> codeBlock("%T()", Poet.Exposed.binaryColumnType)
    Column.Type.Primitive.VARCHAR -> codeBlock("%T()", Poet.Exposed.textColumnType)
    Column.Type.Primitive.DATE -> codeBlock("%T()", Poet.Exposed.kotlinLocalDateColumnType)
    Column.Type.Primitive.INTERVAL -> codeBlock("%T()", Poet.Pgen.intervalColumnType)
    Column.Type.Primitive.INT4RANGE -> codeBlock("%T()", Poet.Pgen.int4RangeColumnType)
    Column.Type.Primitive.INT8RANGE -> codeBlock("%T()", Poet.Pgen.int8RangeColumnType)
    Column.Type.Primitive.INT4MULTIRANGE -> codeBlock("%T()", Poet.Pgen.int4MultiRangeColumnType)
    Column.Type.Primitive.INT8MULTIRANGE -> codeBlock("%T()", Poet.Pgen.int8MultiRangeColumnType)
    Column.Type.Primitive.INT4 -> codeBlock("%T()", Poet.Exposed.integerColumnType)
    Column.Type.Primitive.FLOAT4 -> codeBlock("%T()", Poet.Exposed.floatColumnType)
    Column.Type.Primitive.FLOAT8 -> codeBlock("%T()", Poet.Exposed.doubleColumnType)
    Column.Type.Primitive.INT2 -> codeBlock("%T()", Poet.Exposed.shortColumnType)
    Column.Type.Primitive.TEXT -> codeBlock("%T()", Poet.Exposed.textColumnType)
    Column.Type.Primitive.CITEXT -> codeBlock("%T()", Poet.Pgen.citextColumnType)
    Column.Type.Primitive.TIME -> codeBlock("%T()", Poet.Exposed.kotlinLocalTimeColumnType)
    Column.Type.Primitive.TIMESTAMP -> codeBlock("%T()", Poet.Exposed.kotlinInstantColumnType)
    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> codeBlock("%T()", Poet.Exposed.kotlinOffsetDateTimeColumnType)
    Column.Type.Primitive.UUID -> codeBlock("%T()", c.poet.uuidColumnType)
    Column.Type.Primitive.JSON -> codeBlock("%T()", Poet.Pgen.defaultJsonColumnType)
    Column.Type.Primitive.JSONB -> codeBlock("%T()", Poet.Pgen.defaultJsonColumnType)
    Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> codeBlock("%T()", Poet.Pgen.unconstrainedNumericColumnType)
    Column.Type.Primitive.REG_CLASS -> codeBlock("%T()", Poet.Pgen.regClassColumnType)
    is Column.Type.CustomType -> codeBlock("%T()", type.columnType.poet)
}

internal fun Column.Type.toSqlObjectNameOrNull() = when (this) {
    is Column.Type.NonPrimitive.Domain -> ref
    is Column.Type.NonPrimitive.Enum -> ref
    is Column.Type.NonPrimitive.Composite -> ref
    is Column.Type.Reference,
    is Column.Type.CustomType,
    is Column.Type.Overwrite,
    is Column.Type.NonPrimitive.Array,
    is Column.Type.NonPrimitive.Numeric,
    is Column.Type.NonPrimitive.PgVector,
    is Column.Type.Primitive -> null
}
