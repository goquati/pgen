package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import de.quati.kotlin.util.poet.PackageName
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.spec.PgenSpec
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.DbName
import de.quati.pgen.plugin.intern.model.spec.KotlinValueClass
import de.quati.pgen.plugin.intern.model.spec.SqlColumnName
import de.quati.pgen.plugin.intern.model.spec.Table

internal class CodeGenContext(
    globalConfig: Config.Global,
    val dbConfig: Config.Db,
    val dbSpec: PgenSpec.Database,
    val oas: Config.Oas?,
) {
    val typeGroups = dbSpec.tables.getColumnTypeGroups()
    val rootPackageName = globalConfig.packageName
    val connectionType = globalConfig.connectionType
    val typeMappings = dbConfig.typeMappings.associate { it.sqlType to it.valueClass }
    val enumMappings = dbConfig.enumMappings.associate { it.sqlType to it.enumClass }
    val typeOverwrites = dbConfig.typeOverwrites.associate { it.sqlColumn to it.valueClass }
    val referencesMapping = run {
        val customTypeMappings = dbConfig.columnTypeMappings.associateBy { it.name }
        val refMappings = dbSpec.allTypes.mapNotNull { type ->
            type.toSqlObjectNameOrNull()?.let { it to type }
        }.toMap()
        refMappings + customTypeMappings
    }

    fun resolve(type: Column.Type): Column.Type.Actual = when (type) {
        is Column.Type.Reference -> getRefTypeOrThrow(type)
        is Column.Type.Actual -> type
    }

    private fun getRefTypeOrThrow(ref: Column.Type.Reference): Column.Type.Actual {
        return referencesMapping[ref.name]?.takeIf { it != ref }?.let { type ->
            when (type) {
                is Column.Type.Reference -> getRefTypeOrThrow(type)
                is Column.Type.Actual -> type
            }
        } ?: error("Reference not found: $ref")
    }

    val localConfigContext = oas?.localConfigContext?.let { c ->
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
        val newColumns = this@update.columns.map { column ->
            val columnName = SqlColumnName(tableName = this@update.name, name = column.name.value)
            if (column.type is Column.Type.Overwrite) return@map column
            val kotlinClass = allTypeOverwrites[columnName] ?: return@map column
            val newType = Column.Type.Overwrite(
                valueClass = kotlinClass,
                base = when (val t = column.type) {
                    is Column.Type.NonPrimitive.Domain -> t.base
                    else -> t
                },
            )
            column.copy(type = newType)
        }
        return copy(columns = newColumns)
    }

    val poet = Poet(
        dbName = dbSpec.name,
        rootPackageName = rootPackageName,
        uuidType = globalConfig.uuidType,
    )

    data class Poet(
        private val dbName: DbName,
        val rootPackageName: PackageName,
        private val uuidType: Config.UuidType,
    ) {
        val packageDb = rootPackageName.plus("db.${dbName.prettyName}")
        val packageMapper = rootPackageName.plus("mapper")
        val packageService = rootPackageName.plus("service")

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