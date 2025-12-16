package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.pgen.plugin.dsl.addClass
import de.quati.pgen.plugin.dsl.addCode
import de.quati.pgen.plugin.dsl.addControlFlow
import de.quati.pgen.plugin.dsl.addFunction
import de.quati.pgen.plugin.dsl.addProperty
import de.quati.pgen.plugin.dsl.indent
import de.quati.pgen.plugin.model.sql.Statement
import de.quati.pgen.plugin.dsl.primaryConstructor
import de.quati.pgen.plugin.util.makeDifferent

context(c: CodeGenContext)
internal val Collection<Statement>.packageName
    get() = map { it.name.packageName }.distinct().singleOrNull()
        ?: error("statements from different DB's cannot wirte in the same file")

context(c: CodeGenContext)
internal fun FileSpec.Builder.addStatements(statements: Collection<Statement>) {
    val packageName = statements.packageName
    statements.map { statement ->
        with(statement.name.dbName.toContext()) {
            addClass(statement.name.prettyResultClassName) {
                addModifiers(KModifier.DATA)
                primaryConstructor {
                    statement.columns.forEach { column ->
                        val name = column.name.pretty
                        val type = column.type.getTypeName(innerArrayType = false).copy(nullable = column.isNullable)
                        addParameter(name, type)
                        addProperty(name = name, type = type) { initializer(name) }
                    }
                }
            }
        }
    }
    statements.map { statement ->
        with(statement.name.dbName.toContext()) {
            val resultTypeName = ClassName(packageName.name, statement.name.prettyResultClassName)
            val statementNames = statement.columns.map { it.name.pretty }.toSet() +
                    statement.variables.map { it.pretty }.toSet()
            addFunction(statement.name.prettyName) {
                when (statement.cardinality) {
                    Statement.Cardinality.ONE -> addModifiers(KModifier.SUSPEND)
                    Statement.Cardinality.MANY -> Unit
                }
                receiver(Poet.Exposed.transaction)
                statement.variableTypes.entries.sortedBy { it.key }.forEach { (name, type) ->
                    addParameter(name.pretty, type.getTypeName())
                }
                when (statement.cardinality) {
                    Statement.Cardinality.ONE -> returns(resultTypeName)
                    Statement.Cardinality.MANY -> returns(Poet.flow.parameterizedBy(resultTypeName))
                }

                addCode {
                    val rowSetName = "rowSet".makeDifferent(statementNames)

                    val inputsPairs = buildCodeBlock {
                        statement.variables.forEach { name ->
                            val type = statement.variableTypes[name]!!
                            add("%L to %L,", type.getExposedColumnType(), name.pretty)
                        }
                    }
                    addControlFlow("return %T", Poet.generateChannelFlow) {
                        add("exec(stmt = %S, args = listOf(%L)) { %L ->\n", statement.sql, inputsPairs, rowSetName)
                        indent {
                            addControlFlow("while(%L.next())", rowSetName) {
                                add("%T(\n", Poet.trySendBlocking)
                                indent {
                                    add("%T(\n", resultTypeName)
                                    indent {
                                        statement.columns.forEachIndexed { idx, column ->
                                            add(
                                                "%L = %L.getObject(%L)%L.let { %L.valueFromDB(it) }%L,\n",
                                                column.name.pretty,
                                                rowSetName, idx + 1,
                                                if (column.isNullable) "?" else "!!",
                                                column.type.getExposedColumnType(),
                                                if (column.isNullable) "" else "!!",
                                            )
                                        }
                                    }
                                    add(")\n")
                                }
                                add(")\n")
                            }
                        }
                    }
                    when (statement.cardinality) {
                        Statement.Cardinality.ONE -> add("}.%T()\n", Poet.flowSingle)
                        Statement.Cardinality.MANY -> add("}\n")
                    }
                }
            }
        }
    }
}
