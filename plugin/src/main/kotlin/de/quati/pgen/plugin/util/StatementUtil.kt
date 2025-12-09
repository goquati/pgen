package de.quati.pgen.plugin.util

import de.quati.pgen.plugin.model.sql.Statement
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.walk

private val statementHeaderRegex = Regex("--\\s*name:\\s*")
private val variablesRegex = Regex(
    pattern = "/\\*\\s*\\\$(?<name>\\w+)\\s*\\*/\\s*(?:'[^']*'|[+-]?\\d+(?:\\.\\d+)?|TRUE|FALSE)",
    option = RegexOption.IGNORE_CASE,
)
private val nonNullColumnRegex = Regex("(?:AS|as)\\s*/\\*\\s*nonnull\\*/\\s*(?<name>\\w+)")

@OptIn(ExperimentalPathApi::class)
fun parseStatements(scripts: Set<Path>): List<Statement.Raw> = scripts.flatMap { p ->
    when (p.isDirectory()) {
        true -> p.walk().filter { it.endsWith(".sql") }.toList()
        false -> listOf(p)
    }
}.flatMap { parseStatements(it.readText()) }

fun parseStatements(sqlScript: String): List<Statement.Raw> =
    sqlScript.split(statementHeaderRegex)
        .mapNotNull { statement ->
            statement.split("\n")
                .filter(String::isNotBlank)
                .filter { !it.startsWith("--") }
                .takeIf { it.size > 1 }
                ?.joinToString("\n")
        }
        .map { statement ->
            val header = statement.substringBefore('\n')
            val name = header.substringBefore(' ')
            val options = header.substringAfter(' ').split(':')
                .map(String::trim).filter(String::isNotEmpty).map(String::uppercase).toSet()
            val cardinality = Statement.Cardinality.entries
                .filter { it.name in options }
                .also { if (it.size > 1) error("Multiple cardinality options found in statement '$name'") }
                .singleOrNull() ?: Statement.Cardinality.MANY
            val sql = statement.substringAfter('\n')

            val uniqueSortedVariables = linkedSetOf<Statement.VariableName>()
            val allVariables = mutableListOf<Statement.VariableName>()
            var preparedPsql = sql.replace(variablesRegex) { match ->
                val idx = match.groups["name"]!!.value
                    .let { Statement.VariableName(it) }
                    .let { varName ->
                        allVariables.add(varName)
                        uniqueSortedVariables.add(varName)
                        uniqueSortedVariables.indexOf(varName) + 1
                    }
                "$$idx"
            }

            val nonNullColumns = mutableSetOf<String>()
            val preparedSql = sql
                .replace(variablesRegex) { "?" }
                .replace(nonNullColumnRegex) {
                    val name = it.groups["name"]!!.value
                    nonNullColumns.add(name)
                    "AS $name"
                }

            Statement.Raw(
                name = name,
                cardinality = cardinality,
                allVariables = allVariables,
                uniqueSortedVariables = uniqueSortedVariables.toList(),
                nonNullColumns = nonNullColumns.toSet(),
                sql = sql,
                preparedSql = preparedSql,
                preparedPsql = preparedPsql,
            )
        }
