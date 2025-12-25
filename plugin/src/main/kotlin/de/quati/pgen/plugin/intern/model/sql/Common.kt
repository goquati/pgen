package de.quati.pgen.plugin.intern.model.sql

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.intern.util.codegen.CodeGenContext
import de.quati.pgen.plugin.intern.PackageName
import de.quati.pgen.plugin.intern.util.KotlinClassNameSerializer
import de.quati.pgen.plugin.intern.util.SqlColumnNameSerializer
import de.quati.pgen.plugin.intern.util.SqlObjectNameSerializer
import de.quati.pgen.plugin.intern.util.SqlStatementNameSerializer
import de.quati.pgen.plugin.intern.util.codegen.oas.DbContext
import de.quati.pgen.plugin.intern.util.kotlinKeywords
import de.quati.pgen.plugin.intern.util.makeDifferent
import de.quati.pgen.plugin.intern.util.toCamelCase
import kotlinx.serialization.Serializable

@JvmInline
internal  value class DbName(val name: String) : Comparable<DbName> {
    override fun compareTo(other: DbName) = name.compareTo(other.name)
    override fun toString() = name

    fun toContext() = DbContext.Base(this)

    fun toSchema(schemaName: String) = SchemaName(dbName = this, schemaName = schemaName)
    val schemaPgCatalog get() = toSchema(schemaName = "pg_catalog")

    context(c: CodeGenContext)
    val packageName get() = c.poet.packageDb.plus(name)
}

internal data class SchemaName(val dbName: DbName, val schemaName: String) : Comparable<SchemaName> {
    override fun toString() = "$dbName->$schemaName"

    override fun compareTo(other: SchemaName) =
        dbName.compareTo(other.dbName).takeIf { it != 0 }
            ?: schemaName.compareTo(other.schemaName)
}

internal sealed interface SqlObject : Comparable<SqlObject>, DbContext {
    val name: SqlObjectName
    override val dbName: DbName get() = name.schema.dbName
    override fun compareTo(other: SqlObject) = name.compareTo(other.name)
}

@Serializable(with = SqlStatementNameSerializer::class)
internal data class SqlStatementName(
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
internal data class SqlObjectName(
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
internal data class SqlColumnName(
    val tableName: SqlObjectName,
    val name: String,
) : Comparable<SqlColumnName> {
    override fun compareTo(other: SqlColumnName): Int =
        tableName.compareTo(other.tableName).takeIf { it != 0 }
            ?: name.compareTo(other.name)
}

@Serializable(with = KotlinClassNameSerializer::class)
internal data class KotlinClassName(
    val packageName: String,
    val className: String,
) {
    val poet get() = ClassName(packageName, className)
}

@Serializable
internal data class KotlinValueClass(
    val name: KotlinClassName,
    val parseFunction: String? = null,
)

@Serializable
internal data class KotlinEnumClass(
    val name: KotlinClassName,
    val mappings: Map<String, String> = emptyMap(),
)
