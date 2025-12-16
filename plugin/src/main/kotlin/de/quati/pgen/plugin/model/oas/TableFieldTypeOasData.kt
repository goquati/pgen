package de.quati.pgen.plugin.model.oas

import de.quati.pgen.plugin.model.sql.Column
import de.quati.pgen.plugin.model.sql.SqlObjectName
import de.quati.pgen.plugin.util.toKababCase

sealed interface TableFieldTypeOasData {
    data class Type(
        val type: String,
        val format: String? = null,
    ) : TableFieldTypeOasData

    data class Enum(
        val name: SqlObjectName,
    ) : TableFieldTypeOasData {

        context(c: OasGenContext)
        fun getRef(): String {
            return "./${c.oasCommonName}.yaml#/components/schemas/${name.prettyName}"
        }
    }

    data class Array(
        val items: TableFieldTypeOasData,
    ) : TableFieldTypeOasData

    fun getEnumNameOrNull(): SqlObjectName? = when (this) {
        is Type -> null
        is Enum -> name
        is Array -> items.getEnumNameOrNull()
    }

    companion object {
        fun fromData(type: Column.Type): TableFieldTypeOasData = when (type) {
            is Column.Type.NonPrimitive.Array -> Array(items = fromData(type.elementType))
            is Column.Type.NonPrimitive.Domain -> Type(type = "string", format = type.name.name.toKababCase())
            is Column.Type.NonPrimitive.Reference ->
                Type(type = "string", format = type.valueClassName.className.toKababCase())

            is Column.Type.NonPrimitive.Enum -> Enum(name = type.name)
            is Column.Type.NonPrimitive.Numeric -> Type(type = "number")
            is Column.Type.NonPrimitive.Composite -> Type(type = "string")
            is Column.Type.NonPrimitive.PgVector -> Type(type = "string")
            Column.Type.Primitive.BOOL -> Type(type = "boolean")
            Column.Type.Primitive.BINARY -> Type(type = "string", format = "byte")
            Column.Type.Primitive.DATE -> Type(type = "string", format = "date")
            Column.Type.Primitive.INT2 -> Type(type = "integer", format = "int16")
            Column.Type.Primitive.INT4 -> Type(type = "integer", format = "int32")
            Column.Type.Primitive.INT8 -> Type(type = "integer", format = "int64")
            Column.Type.Primitive.FLOAT4 -> Type(type = "number", format = "float")
            Column.Type.Primitive.FLOAT8 -> Type(type = "number", format = "double")
            Column.Type.Primitive.INT4RANGE -> Type(type = "string")
            Column.Type.Primitive.INT8RANGE -> Type(type = "string")
            Column.Type.Primitive.INT4MULTIRANGE -> Type(type = "string")
            Column.Type.Primitive.INT8MULTIRANGE -> Type(type = "string")
            Column.Type.Primitive.INTERVAL -> Type(type = "string")
            Column.Type.Primitive.JSON -> Type(type = "string")
            Column.Type.Primitive.JSONB -> Type(type = "string")
            Column.Type.Primitive.TEXT -> Type(type = "string")
            Column.Type.Primitive.INET -> Type(type = "string")
            Column.Type.Primitive.CIDR -> Type(type = "string")
            Column.Type.Primitive.CITEXT -> Type(type = "string")
            Column.Type.Primitive.TIME -> Type(type = "string", format = "time")
            Column.Type.Primitive.TIMESTAMP -> Type(type = "string", format = "date-time")
            Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE -> Type(type = "string", format = "date-time")
            Column.Type.Primitive.UUID -> Type(type = "string", format = "uuid")
            Column.Type.Primitive.VARCHAR -> Type(type = "string")
            Column.Type.Primitive.UNCONSTRAINED_NUMERIC -> Type(type = "number")
            Column.Type.Primitive.REG_CLASS -> Type(type = "string", format = "regclass")
        }
    }
}