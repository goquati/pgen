package de.quati.pgen.plugin.model.sql

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import de.quati.pgen.plugin.util.ColumnTypeSerializer
import de.quati.pgen.plugin.util.codegen.CodeGenContext
import de.quati.pgen.plugin.util.makeDifferent
import de.quati.pgen.plugin.util.toCamelCase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
data class Column(
    @Transient val pos: Int = -1,
    val name: Name,
    val type: Type,
    val isNullable: Boolean = false,
    val default: String? = null,
) {
    val prettyName get() = name.pretty


    @JvmInline
    @Serializable
    value class Name(val value: String) {
        override fun toString() = value
        val pretty get() = value.toCamelCase(capitalized = false).makeDifferent(reservedColumnNames)

        companion object {
            private val reservedColumnNames = setOf(
                "tableName",
                "schemaName",
                "tableNameWithoutScheme",
                "tableNameWithoutSchemeSanitized",
                "_columns",
                "columns",
                "autoIncColumn",
                "_indices",
                "indices",
                "_foreignKeys",
                "foreignKeys",
                "sequences",
                "checkConstraints",
                "generatedUnsignedCheckPrefix",
                "generatedSignedCheckPrefix",
                "primaryKey",
                "ddl",
                "source",
                "fields",
            )
        }
    }

    @Serializable(with = ColumnTypeSerializer::class)
    sealed interface Type {
        val sqlType: String

        @Serializable
        sealed interface NonPrimitive : Type {
            val primitiveElementTypeOrNull: Primitive? get() = null

            @Serializable
            @SerialName("array")
            data class Array(val elementType: Type) : NonPrimitive {
                override val sqlType get() = "${elementType.sqlType}[]"

                override val primitiveElementTypeOrNull: Primitive?
                    get() = when (elementType) {
                        is Primitive -> elementType
                        is Array -> elementType.primitiveElementTypeOrNull
                        else -> null
                    }
            }

            @Serializable
            @SerialName("enum")
            data class Enum(val name: SqlObjectName) : NonPrimitive {
                override val sqlType get() = "${name.schema.schemaName}.${name.name}"
            }

            @Serializable
            @SerialName("composite")
            data class Composite(val name: SqlObjectName) : NonPrimitive {
                override val sqlType get() = "${name.schema.schemaName}.${name.name}"

                context(c: CodeGenContext)
                fun getColumnTypeTypeName() = ClassName("${name.packageName}", "${name.prettyName}.ColumnType")
            }


            @Serializable
            @SerialName("pgvector")
            data class PgVector(val schema: String) : NonPrimitive {
                override val sqlType get() = "$schema.$VECTOR_NAME"

                companion object {
                    const val VECTOR_NAME = "vector"
                }
            }

            @Serializable
            @SerialName("numeric")
            data class Numeric(val precision: Int, val scale: Int) : NonPrimitive {
                override val sqlType get() = ""
            }

            sealed interface DomainType : NonPrimitive {
                context(c: CodeGenContext)
                fun getDomainTypename(): TypeName

                context(c: CodeGenContext)
                fun getValueClass(): KotlinValueClass

                val originalType: Type

                context(c: CodeGenContext)
                val parserFunction: String
                    get() = getValueClass().parseFunction?.let { ".$it" } ?: ""
            }

            @Serializable
            @SerialName("domain")
            data class Domain(
                override val name: SqlObjectName,
                override val originalType: Type
            ) : SqlObject, DomainType {
                override val sqlType get() = "${name.schema.schemaName}.${name.name}"

                context(c: CodeGenContext)
                override fun getDomainTypename(): TypeName = c.typeMappings[name]?.name?.poet ?: name.typeName

                context(c: CodeGenContext)
                override fun getValueClass(): KotlinValueClass = c.typeMappings[name] ?: KotlinValueClass(
                    name = KotlinClassName(
                        packageName = name.packageName.name,
                        className = name.prettyName
                    ),
                    parseFunction = null,
                )
            }

            @Serializable
            @SerialName("reference")
            data class Reference(
                private val valueClass: KotlinValueClass,
                override val originalType: Type,
            ) : DomainType {
                val valueClassName get() = valueClass.name
                override val sqlType: String get() = originalType.sqlType

                context(c: CodeGenContext)
                override fun getDomainTypename(): TypeName = valueClass.name.poet

                context(c: CodeGenContext)
                override fun getValueClass(): KotlinValueClass = valueClass
            }
        }

        @Serializable
        enum class Primitive(override val sqlType: String) : Type {
            BOOL("bool"),
            BINARY("bytea"),
            DATE("date"),
            INT2("int2"),
            INT4("int4"),
            INT8("int8"),
            INET("inet"),
            CIDR("cidr"),
            FLOAT4("float4"),
            FLOAT8("float8"),
            INT4RANGE("int4range"),
            INT8RANGE("int8range"),
            INT4MULTIRANGE("int4multirange"),
            INT8MULTIRANGE("int8multirange"),
            INTERVAL("interval"),
            JSON("json"),
            JSONB("jsonb"),
            TEXT("text"),
            CITEXT("citext"),
            TIME("time"),
            TIMESTAMP("timestamp"),
            TIMESTAMP_WITH_TIMEZONE("timestamptz"),
            UUID("uuid"),
            VARCHAR("varchar"),
            UNCONSTRAINED_NUMERIC("numeric"),
            REG_CLASS("regclass"),
        }
    }
}