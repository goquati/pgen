package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import de.quati.kotlin.util.poet.PackageName
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.sql.Column
import de.quati.pgen.plugin.intern.model.sql.KotlinValueClass
import de.quati.pgen.plugin.intern.model.sql.SqlColumnName
import de.quati.pgen.plugin.intern.model.sql.Table

internal class CodeGenContext(
    config: Config,
    specContext: SpecContext,
    typeGroups: List<Set<SqlColumnName>>,
) : SpecContext by specContext {
    val rootPackageName = config.packageName
    val connectionType = config.connectionType
    val typeMappings = config.dbConfigs.flatMap(Config.Db::typeMappings)
        .associate { it.sqlType to it.valueClass }
    val enumMappings = config.dbConfigs.flatMap(Config.Db::enumMappings)
        .associate { it.sqlType to it.enumClass }
    val typeOverwrites = config.dbConfigs.flatMap(Config.Db::typeOverwrites)
        .associate { it.sqlColumn to it.valueClass }
    val localConfigContext = config.oas?.localConfigContext?.let { c ->
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
            if (column.type is Column.Type.NonPrimitive.Overwrite) return@map column
            val kotlinClass = allTypeOverwrites[columnName] ?: return@map column
            val newType = Column.Type.NonPrimitive.Overwrite(
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
        uuidType = config.uuidType,
    )

    data class Poet(
        val rootPackageName: PackageName,
        private val uuidType: Config.UuidType,
    ) {
        val packageDb = PackageName("$rootPackageName.db")
        val packageMapper = PackageName("$rootPackageName.mapper")
        val packageService = PackageName("$rootPackageName.service")

        val uuidColumnType = when (uuidType) {
            Config.UuidType.KOTLIN -> ClassName("org.jetbrains.exposed.v1.core", "UuidColumnType")
            Config.UuidType.JAVA -> ClassName("org.jetbrains.exposed.v1.core.java", "UUIDColumnType")
        }

        val uuid = when (uuidType) {
            Config.UuidType.KOTLIN -> ClassName("kotlin.uuid", "Uuid")
            Config.UuidType.JAVA -> ClassName("java.util", "UUID")
        }

        val uuidColumn = when (uuidType) {
            Config.UuidType.KOTLIN -> CodeBlock.of("uuid")
            Config.UuidType.JAVA -> CodeBlock.of("%T", ClassName("org.jetbrains.exposed.v1.core.java", "javaUUID"))
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