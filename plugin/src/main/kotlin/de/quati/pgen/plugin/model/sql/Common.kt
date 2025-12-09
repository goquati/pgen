package de.quati.pgen.plugin.model.sql

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.util.codegen.CodeGenContext
import de.quati.pgen.plugin.dsl.PackageName
import de.quati.pgen.plugin.util.KotlinClassNameSerializer
import de.quati.pgen.plugin.util.SqlColumnNameSerializer
import de.quati.pgen.plugin.util.SqlObjectNameSerializer
import de.quati.pgen.plugin.util.SqlStatementNameSerializer
import de.quati.pgen.plugin.util.kotlinKeywords
import de.quati.pgen.plugin.util.makeDifferent
import de.quati.pgen.plugin.util.toCamelCase
import kotlinx.serialization.Serializable

@JvmInline
value class DbName(val name: String) : Comparable<DbName> {
    override fun compareTo(other: DbName) = name.compareTo(other.name)
    override fun toString() = name

    fun toSchema(schemaName: String) = SchemaName(dbName = this, schemaName = schemaName)
    val schemaPgCatalog get() = toSchema(schemaName = "pg_catalog")

    context(c: CodeGenContext)
    val packageName get() = c.poet.packageDb.plus(name)
}

data class SchemaName(val dbName: DbName, val schemaName: String) : Comparable<SchemaName> {
    override fun toString() = "$dbName->$schemaName"

    override fun compareTo(other: SchemaName) =
        dbName.compareTo(other.dbName).takeIf { it != 0 }
            ?: schemaName.compareTo(other.schemaName)
}

sealed interface SqlObject : Comparable<SqlObject> {
    val name: SqlObjectName
    override fun compareTo(other: SqlObject) = name.compareTo(other.name)
}

@Serializable(with = SqlStatementNameSerializer::class)
data class SqlStatementName(
    val dbName: DbName,
    val name: String,
) : Comparable<SqlStatementName> {

    val prettyName get() = name.toCamelCase(capitalized = false)
    val prettyResultClassName get() = name.toCamelCase(capitalized = true) + "Result"

    context(c: CodeGenContext)
    val packageName get() = dbName.packageName

    context(c: CodeGenContext)
    val typeName
        get() = ClassName(packageName.name, prettyName)

    override fun compareTo(other: SqlStatementName): Int =
        dbName.compareTo(other.dbName).takeIf { it != 0 }
            ?: name.compareTo(other.name)
}

@Serializable(with = SqlObjectNameSerializer::class)
data class SqlObjectName(
    val schema: SchemaName,
    val name: String,
) : Comparable<SqlObjectName> {

    val prettyName get() = name.toCamelCase(capitalized = true)

    context(c: CodeGenContext)
    val packageName
        get() = PackageName(
            "${c.poet.packageDb}.${schema.dbName}.${
                schema.schemaName.makeDifferent(kotlinKeywords)
            }"
        )

    context(c: CodeGenContext)
    val typeName
        get() = ClassName(packageName.name, prettyName)

    override fun compareTo(other: SqlObjectName): Int =
        schema.compareTo(other.schema).takeIf { it != 0 }
            ?: name.compareTo(other.name)
}

@Serializable(with = SqlColumnNameSerializer::class)
data class SqlColumnName(
    val tableName: SqlObjectName,
    val name: String,
) : Comparable<SqlColumnName> {
    override fun compareTo(other: SqlColumnName): Int =
        tableName.compareTo(other.tableName).takeIf { it != 0 }
            ?: name.compareTo(other.name)
}

@Serializable(with = KotlinClassNameSerializer::class)
data class KotlinClassName(
    val packageName: String,
    val className: String,
) {
    val poet get() = ClassName(packageName, className)
}

@Serializable
data class KotlinValueClass(
    val name: KotlinClassName,
    val parseFunction: String? = null,
)

@Serializable
data class KotlinEnumClass(
    val name: KotlinClassName,
    val mappings: Map<String, String> = emptyMap(),
)
