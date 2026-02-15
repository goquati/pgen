package de.quati.pgen.plugin.intern.model.spec

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import de.quati.kotlin.util.poet.kotlinKeywords
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.kotlin.util.poet.toCamelCase
import de.quati.kotlin.util.poet.toSnakeCase
import de.quati.pgen.plugin.intern.util.SqlObjectNameSerializer
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
internal value class DbName(val name: String) : Comparable<DbName> {
    override fun toString() = name
    override fun compareTo(other: DbName) = name.compareTo(other.name)
    val prettyName get() = name.toSnakeCase(uppercase = false).makeDifferent(kotlinKeywords, "")

}

@JvmInline
@Serializable
internal value class SchemaName(val name: String) : Comparable<SchemaName> {
    override fun toString() = name
    override fun compareTo(other: SchemaName) = name.compareTo(other.name)
    val prettyName get() = name.toSnakeCase(uppercase = false).makeDifferent(kotlinKeywords, "")

    context(c: CodeGenContext)
    val packageName get() = c.poet.packageDb.plus(prettyName)

    companion object {
        val PgCatalog = SchemaName("pg_catalog")
    }
}

internal sealed interface SqlObject : Comparable<SqlObject> {
    val name: SqlObjectName
    override fun compareTo(other: SqlObject) = name.compareTo(other.name)
}

@JvmInline
@Serializable
internal value class SqlStatementName(val name: String) : Comparable<SqlStatementName> {
    val prettyName get() = name.toCamelCase(capitalized = false)
    val prettyResultClassName get() = name.toCamelCase(capitalized = true) + "Result"

    context(c: CodeGenContext)
    val typeName
        get() = c.poet.packageDb.className(prettyName)

    override fun compareTo(other: SqlStatementName): Int = name.compareTo(other.name)
}

@JvmInline
@Serializable
internal value class SqlType(val name: String) : Comparable<SqlType> {
    override fun toString() = name
    override fun compareTo(other: SqlType) = name.compareTo(other.name)
    fun toArrayType() = SqlType("$name[]")

    companion object {
        fun parse(name: String) = SqlType(name.lowercase())
    }
}

@Serializable(with = SqlObjectNameSerializer::class)
internal data class SqlObjectName(
    val schema: SchemaName,
    val name: String,
) : Comparable<SqlObjectName> {
    override fun toString() = "$schema.$name"
    fun toSqlType() = SqlType("$schema.$name")

    val prettyName get() = name.toCamelCase(capitalized = true)

    context(c: CodeGenContext)
    val packageName get() = schema.packageName

    context(c: CodeGenContext)
    val typeName
        get() = ClassName(packageName.name, prettyName)

    override fun compareTo(other: SqlObjectName): Int =
        schema.compareTo(other.schema).takeIf { it != 0 }
            ?: name.compareTo(other.name)

    companion object {
        fun parse(type: SqlType) = parse(type.name)
        fun parse(str: String): SqlObjectName {
            val (schema, name) = str.split('.').takeIf { it.size == 2 }
                ?: error("invalid sql object name '$str', expected format <schema>.<name>")
            return SqlObjectName(SchemaName(schema.lowercase()), name.lowercase())
        }
    }
}

internal data class SqlColumnName(
    val tableName: SqlObjectName,
    val name: String,
) : Comparable<SqlColumnName> {
    override fun toString() = "$tableName.$name"
    override fun compareTo(other: SqlColumnName): Int =
        tableName.compareTo(other.tableName).takeIf { it != 0 }
            ?: name.compareTo(other.name)

    companion object {
        fun parse(str: String): SqlColumnName {
            val (schema, table, column) = str.split('.').takeIf { it.size == 3 }
                ?: error("invalid sql column name '$str', expected format <schema>.<table-name>.<column-name>")
            return SqlColumnName(
                tableName = SqlObjectName(SchemaName(schema.lowercase()), table.lowercase()),
                name = column.lowercase(),
            )
        }
    }
}

internal data class KotlinClassName(
    val packageName: String,
    val className: String,
) {
    val poet get() = ClassName(packageName, className)
}

internal data class KotlinValueClass(
    val name: KotlinClassName,
    val parseFunction: String? = null,
)

internal data class KotlinEnumClass(
    val name: KotlinClassName,
    val mappings: Map<String, String> = emptyMap(),
)
