package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import de.quati.kotlin.util.poet.PackageName
import de.quati.pgen.plugin.intern.model.config.ColumnTypeMapping
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.config.Config.Oas.LocalConfigContext
import de.quati.pgen.plugin.intern.model.sql.Column
import de.quati.pgen.plugin.intern.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.intern.model.sql.KotlinValueClass
import de.quati.pgen.plugin.intern.model.sql.SqlColumnName
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import de.quati.pgen.plugin.intern.model.sql.Table
import de.quati.pgen.plugin.intern.util.codegen.oas.DbContext

internal class CodeGenContext(
    rootPackageName: PackageName,
    val typeMappings: Map<SqlObjectName, KotlinValueClass>,
    val enumMappings: Map<SqlObjectName, KotlinEnumClass>,
    columnTypeMappings: Collection<ColumnTypeMapping>,
    typeOverwrites: Map<SqlColumnName, KotlinValueClass>,
    typeGroups: List<Set<SqlColumnName>>,
    val connectionType: Config.ConnectionType,
    localConfigContext: LocalConfigContext?,
) {
    val columnTypeMappings = columnTypeMappings.associateBy { it.sqlType }

    context(d: DbContext)
    fun getColumnTypeMapping(type: Column.Type.CustomPrimitive) =
        columnTypeMappings[type.sqlObjectName] ?: error("no column type mapping for ${type.sqlObjectName}")

    val localConfigContext = localConfigContext?.let { c ->
        c.copy(
            type = ClassName(
                c.type.packageName.takeIf { it != "default" } ?: "$rootPackageName.shared",
                c.type.simpleName,
            )
        )
    }

    val allTypeOverwrites: Map<SqlColumnName, KotlinValueClass> = typeOverwrites.entries.flatMap { (column, clazz) ->
        val group = typeGroups.firstOrNull { it.contains(column) } ?: setOf(column)
        group.map { c -> c to clazz }
    }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }
        .mapValues {
            it.value.singleOrNull() ?: error("multiple type overwrites for ${it.key}: ${it.value}")
        }

    fun Table.update(): Table {
        val newColumns = columns.map { column ->
            val columnName = SqlColumnName(tableName = name, name = column.name.value)
            if (column.type is Column.Type.NonPrimitive.Reference) return@map column
            val kotlinClass = allTypeOverwrites[columnName] ?: return@map column
            val newType = Column.Type.NonPrimitive.Reference(
                valueClass = kotlinClass,
                originalType = when (val t = column.type) {
                    is Column.Type.NonPrimitive.Domain -> t.originalType
                    else -> t
                },
            )
            column.copy(type = newType)
        }
        return copy(columns = newColumns)
    }

    val poet = Poet(
        rootPackageName = rootPackageName,
        kotlinUuidType = false, // TODO
    )

    data class Poet(
        val rootPackageName: PackageName,
        private val kotlinUuidType: Boolean,
    ) {
        val packageDb = PackageName("$rootPackageName.db")
        val packageMapper = PackageName("$rootPackageName.mapper")
        val packageService = PackageName("$rootPackageName.service")

        val uuidColumnType = when (kotlinUuidType) {
            true -> ClassName("org.jetbrains.exposed.v1.core", "UuidColumnType")
            false -> ClassName("org.jetbrains.exposed.v1.core.java", "UUIDColumnType")
        }

        val uuid = when (kotlinUuidType) {
            true -> ClassName("kotlin.uuid", "Uuid")
            false -> ClassName("java.util", "UUID")
        }

        val uuidColumn = when (kotlinUuidType) {
            true -> CodeBlock.of("uuid")
            false -> CodeBlock.of("%T", ClassName("org.jetbrains.exposed.v1.core.java", "javaUUID"))
        }

    }

    companion object {

        fun Collection<Table>.getColumnTypeGroups(): List<Set<SqlColumnName>> {
            return flatMap { table ->
                table.foreignKeys.flatMap { keySet ->
                    keySet.references.map { reference ->
                        SqlColumnName(
                            tableName = table.name,
                            name = reference.sourceColumn.value
                        ) to SqlColumnName(
                            tableName = keySet.targetTable,
                            name = reference.targetColumn.value
                        )
                    }
                }
            }.flatMap { (a, b) -> listOf(a to b, b to a) }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .map { it.value.toSet() + setOf(it.key) }
                .mergeIntersections()
        }

        private fun <T> List<Set<T>>.mergeIntersections(): List<Set<T>> {
            val result = mutableListOf<Set<T>>()
            var remaining = toMutableList()
            while (remaining.isNotEmpty()) {
                val group = remaining.removeFirst().toMutableSet()
                while (true) {
                    val (intersects, nonIntersects) = remaining.partition { it.intersect(group).isNotEmpty() }
                    if (intersects.isEmpty()) break
                    group += intersects.flatten().toSet()
                    remaining = nonIntersects.toMutableList()
                }
                result.add(group)
            }
            return result
        }
    }
}