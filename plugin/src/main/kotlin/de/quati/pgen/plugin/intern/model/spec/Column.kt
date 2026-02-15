package de.quati.pgen.plugin.intern.model.spec

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.kotlin.util.poet.toCamelCase
import de.quati.pgen.plugin.intern.util.ColumnTypeSerializer
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator


@Serializable
internal data class Column(
    @Transient val pos: Int = -1,
    val name: Name,
    val type: Type,
    val nullable: Boolean = false,
    val defaultExpr: String? = null,
) {
    val prettyName get() = name.pretty


    @JvmInline
    @Serializable
    value class Name(val value: String) {
        override fun toString() = value
        val pretty get() = value.toCamelCase(capitalized = false).makeDifferent(reservedColumnNames, "")

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

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable(with = ColumnTypeSerializer::class)
    @JsonClassDiscriminator("kind")
    sealed interface Type {
        val sqlType: SqlType

        sealed interface Actual : Type

        data class Reference(val name: SqlObjectName) : Type {
            override val sqlType get() = name.toSqlType()
        }

        data class CustomType(
            val name: SqlObjectName,
            val columnType: KotlinClassName,
            val value: KotlinClassName,
        ) : Actual {
            override val sqlType get() = name.toSqlType()
            fun toRef() = Reference(name)
        }

        sealed interface DomainType : Actual {
            context(c: CodeGenContext)
            fun getDomainTypename(): TypeName

            context(c: CodeGenContext)
            fun getValueClass(): KotlinValueClass

            val base: Type

            context(c: CodeGenContext)
            val parserFunction: String
                get() = getValueClass().parseFunction?.let { ".$it" } ?: ""
        }

        data class Overwrite(
            private val valueClass: KotlinValueClass,
            override val base: Type,
        ) : DomainType {
            val valueClassName get() = valueClass.name
            override val sqlType get() = base.sqlType

            context(c: CodeGenContext)
            override fun getDomainTypename(): TypeName = valueClass.name.poet

            context(c: CodeGenContext)
            override fun getValueClass(): KotlinValueClass = valueClass
        }

        @Serializable
        @JsonClassDiscriminator("kind")
        sealed interface NonPrimitive : Actual {

            @Serializable
            @SerialName("array")
            data class Array(val element: Type) : NonPrimitive {
                override val sqlType get() = element.sqlType.toArrayType()
            }

            @Serializable
            @SerialName("enum")
            data class Enum(val ref: SqlObjectName) : NonPrimitive {
                override val sqlType get() = ref.toSqlType()
            }

            @Serializable
            @SerialName("composite")
            data class Composite(val ref: SqlObjectName) : NonPrimitive {
                override val sqlType get() = ref.toSqlType()

                context(c: CodeGenContext)
                fun getColumnTypeTypeName() = ClassName("${ref.packageName}", "${ref.prettyName}.ColumnType")
            }


            @Serializable
            @SerialName("pgvector")
            data class PgVector(val schema: SchemaName) : NonPrimitive {
                override val sqlType get() = SqlType("$schema.$VECTOR_NAME")

                companion object {
                    const val VECTOR_NAME = "vector"
                }
            }

            @Serializable
            @SerialName("numeric")
            data class Numeric(val precision: Int, val scale: Int) : NonPrimitive {
                override val sqlType get() = SqlType("numeric($precision,$scale)")
            }

            @Serializable
            @SerialName("domain")
            data class Domain(
                val ref: SqlObjectName,
                override val base: Type
            ) : SqlObject, DomainType, NonPrimitive {
                override val sqlType get() = ref.toSqlType()
                override val name get() = ref

                context(c: CodeGenContext)
                override fun getDomainTypename(): TypeName = c.typeMappings[ref]?.name?.poet ?: ref.typeName

                context(c: CodeGenContext)
                override fun getValueClass(): KotlinValueClass = c.typeMappings[ref] ?: KotlinValueClass(
                    name = KotlinClassName(
                        packageName = ref.packageName.name,
                        className = ref.prettyName
                    ),
                    parseFunction = null,
                )
            }
        }

        enum class Primitive(private val sqlTypeName: String) : Actual {
            BOOL("bool"),
            BINARY("bytea"),
            DATE("date"),
            INT2("int2"),
            INT4("int4"),
            INT8("int8"),
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
            REG_CLASS("regclass");

            override val sqlType get() = SqlType(sqlTypeName)
        }
    }
}