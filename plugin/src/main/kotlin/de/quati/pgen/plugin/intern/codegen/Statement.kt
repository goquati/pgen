package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addControlFlow
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.pgen.plugin.intern.model.spec.Statement
import de.quati.kotlin.util.poet.makeDifferent

context(c: CodeGenContext)
internal fun FileSpec.Builder.addStatements(statements: Collection<Statement>) {
    statements.forEach { statement ->
        addClass(statement.name.prettyResultClassName) {
            addModifiers(KModifier.DATA)
            primaryConstructor {
                statement.columns.forEach { column ->
                    val name = column.name.pretty
                    val type = column.type.getTypeName(innerArrayType = false).copy(nullable = column.nullable)
                    addParameter(name, type)
                    addProperty(name = name, type = type) { initializer(name) }
                }
            }
        }
    }
    statements.forEach { statement ->
        val resultTypeName = c.poet.packageDb.className(statement.name.prettyResultClassName)
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
                val rowSetName = "rowSet".makeDifferent(statementNames, "")

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
                                            if (column.nullable) "?" else "!!",
                                            column.type.getExposedColumnType(),
                                            if (column.nullable) "" else "!!",
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
