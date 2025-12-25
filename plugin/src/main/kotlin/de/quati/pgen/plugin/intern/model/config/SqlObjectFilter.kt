package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.sql.SchemaName
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName

internal sealed interface SqlObjectFilter {
    fun toFilterString(schemaField: String, tableField: String): String
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean = !isEmpty()
    fun exactSizeOrNull(): Int?

    data class Schemas(val schemaNames: Set<SchemaName>) : SqlObjectFilter {
        override fun toFilterString(schemaField: String, tableField: String): String {
            val schemasStr = schemaNames.toSet().joinToString(",") { "'${it.schemaName}'" }
            return "$schemaField IN ($schemasStr)"
        }

        override fun isEmpty(): Boolean = schemaNames.isEmpty()
        override fun exactSizeOrNull(): Int? = if (isEmpty()) 0 else null
    }

    data class Objects(val objectNames: Set<SqlObjectName>) : SqlObjectFilter {
        override fun toFilterString(schemaField: String, tableField: String): String {
            val objectsStr = objectNames.joinToString(",") { "('${it.schema.schemaName}','${it.name}')" }
            return "($schemaField, $tableField) IN ($objectsStr)"
        }

        override fun isEmpty(): Boolean = objectNames.isEmpty()
        override fun exactSizeOrNull(): Int = objectNames.size
    }

    data class TempTable(val names: Set<String>) : SqlObjectFilter {
        override fun toFilterString(schemaField: String, tableField: String): String {
            val schemasStr = names.toSet().joinToString(",") { "'$it'" }
            return "$tableField IN ($schemasStr)"
        }

        override fun isEmpty(): Boolean = names.isEmpty()
        override fun exactSizeOrNull(): Int = names.size
    }

    data class Multi(
        val filters: List<SqlObjectFilter>,
    ) : SqlObjectFilter {

        override fun toFilterString(schemaField: String, tableField: String): String {
            val filterStrings = filters
                .filter { it.isNotEmpty() }
                .map { it.toFilterString(schemaField = schemaField, tableField = tableField) }
            return when (filterStrings.size) {
                0 -> error("cannot create sql filter for empty generic filter")
                1 -> filterStrings.single()
                else -> "(" + filterStrings.joinToString(" OR ") + ")"
            }
        }

        override fun isEmpty(): Boolean = filters.isEmpty() || filters.all { it.isEmpty() }
        override fun exactSizeOrNull(): Int? = filters.map { it.exactSizeOrNull() }
            .fold<Int?, Int?>(initial = 0) { a, b ->
                if (a != null && b != null) a + b else null
            }
    }
}