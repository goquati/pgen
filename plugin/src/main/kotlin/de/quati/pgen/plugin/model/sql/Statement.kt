package de.quati.pgen.plugin.model.sql

import de.quati.pgen.plugin.util.kotlinKeywords
import de.quati.pgen.plugin.util.makeDifferent
import de.quati.pgen.plugin.util.toCamelCase
import kotlinx.serialization.Serializable


@Serializable
data class Statement(
    val name: SqlStatementName,
    val cardinality: Cardinality,
    val variables: List<VariableName>,
    val variableTypes: Map<VariableName, Column.Type>,
    val columns: List<Column>,
    val sql: String,
) {
    @JvmInline
    @Serializable
    value class VariableName(val name: String) : Comparable<VariableName> {
        val pretty get() = name.toCamelCase(capitalized = false)
            .makeDifferent(kotlinKeywords  + setOf("coroutineContext", "db"))
        override fun compareTo(other: VariableName): Int = name.compareTo(other.name)
    }

    data class Raw(
        val name: String,
        val cardinality: Cardinality,
        val allVariables: List<VariableName>,
        val uniqueSortedVariables: List<VariableName>,
        val nonNullColumns: Set<String>,
        val sql: String,
        val preparedPsql: String,
        val preparedSql: String,
    )

    @Serializable
    enum class Cardinality {
        ONE,
        MANY,
    }
}
