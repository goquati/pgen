package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.dsl.addCode
import de.quati.pgen.plugin.dsl.addCompanionObject
import de.quati.pgen.plugin.dsl.addFunction
import de.quati.pgen.plugin.dsl.addInitializerBlock
import de.quati.pgen.plugin.dsl.addParameter
import de.quati.pgen.plugin.dsl.addProperty
import de.quati.pgen.plugin.dsl.buildDataClass
import de.quati.pgen.plugin.dsl.buildObject
import de.quati.pgen.plugin.dsl.primaryConstructor
import de.quati.pgen.plugin.model.sql.Table
import de.quati.pgen.plugin.util.makeDifferent
import de.quati.pgen.plugin.util.toCamelCase

context(c: CodeGenContext)
internal fun Table.toTypeSpecInternal() = buildObject(this@toTypeSpecInternal.name.prettyName) {
    val foreignKeysSingle = this@toTypeSpecInternal.foreignKeys.map { it.toTyped() }
        .filterIsInstance<Table.ForeignKeyTyped.SingleKey>()
        .associateBy { it.reference.sourceColumn }
    superclass(Poet.Exposed.table)
    addSuperclassConstructorParameter(
        "%S",
        "${this@toTypeSpecInternal.name.schema.schemaName}.${this@toTypeSpecInternal.name.name}"
    )
    this@toTypeSpecInternal.columns.forEach { column ->
        addProperty(
            name = column.prettyName,
            type = Poet.Exposed.column.parameterizedBy(column.getColumnTypeName()),
        ) {
            val postArgs = mutableListOf<Any>()
            val postfix = buildString {
                foreignKeysSingle[column.name]?.let { fKey ->
                    append(".references(ref = %T.${fKey.reference.targetColumn.pretty}, fkName = %S)")
                    postArgs.add(fKey.targetTable.typeName)
                    postArgs.add(fKey.name)
                }
                if (column.isNullable)
                    append(".nullable()")
            }
            initializer(column, postfix = postfix, postArgs = postArgs)
        }
    }
    if (this@toTypeSpecInternal.primaryKey != null) {
        val columnNames = this@toTypeSpecInternal.columns.map { it.prettyName }
        addProperty(name = "primaryKey".makeDifferent(columnNames), type = Poet.Exposed.primaryKey) {
            addModifiers(KModifier.OVERRIDE)
            initializer(
                "PrimaryKey(%L, name = %S)",
                this@toTypeSpecInternal.primaryKey.columnNames.joinToString(", ") { it.pretty },
                this@toTypeSpecInternal.primaryKey.keyName,
            )
        }
    }

    val foreignKeysMulti = this@toTypeSpecInternal.foreignKeys.map { it.toTyped() }
        .filterIsInstance<Table.ForeignKeyTyped.MultiKey>()
    if (foreignKeysMulti.isNotEmpty())
        addInitializerBlock {
            foreignKeysMulti.forEach { foreignKey ->
                val foreignKeyStrFormat = foreignKey.references.joinToString(", ") { ref ->
                    "${ref.sourceColumn.pretty} to %T.${ref.targetColumn.pretty}"
                }
                val foreignKeyStrValues = foreignKey.references.map {
                    foreignKey.targetTable.typeName
                }.toTypedArray()
                @Suppress("SpreadOperator") addStatement(
                    "foreignKey($foreignKeyStrFormat)",
                    *foreignKeyStrValues,
                )
            }
        }

    addType(toConstraintsObject())
    addType(toTypeSpecEntity())
    addType(toTypeSpecUpdateEntity())
    addType(toTypeSpecCreateEntity())
}

context(c: CodeGenContext)
private fun Table.toConstraintsObject() = buildObject(this@toConstraintsObject.constraintsTypeName.simpleName) {
    fun addConstraint(
        name: String,
        clazz: ClassName,
        additionalFormat: String = "",
        additionalArgs: Array<Any> = arrayOf(),
    ) = addProperty(
        name = name.toCamelCase(capitalized = false),
        type = clazz,
    ) {
        @Suppress("SpreadOperator")
        initializer(
            "%T(table = %L, name = %S$additionalFormat)",
            clazz,
            this@toConstraintsObject.name.prettyName,
            name,
            *additionalArgs,
        )
    }

    this@toConstraintsObject.primaryKey?.also { pkey ->
        addConstraint(name = pkey.keyName, clazz = Poet.Pgen.pKeyConstraint)
    }
    this@toConstraintsObject.foreignKeys.forEach { fkey ->
        addConstraint(name = fkey.name, clazz = Poet.Pgen.fKeyConstraint)
    }
    this@toConstraintsObject.uniqueConstraints.forEach { name ->
        addConstraint(name = name, clazz = Poet.Pgen.uniqueConstraint)
    }
    this@toConstraintsObject.checkConstraints.forEach { name ->
        addConstraint(name = name, clazz = Poet.Pgen.checkConstraint)
    }
    this@toConstraintsObject.columns.filter { !it.isNullable }.forEach { column ->
        val name = column.name.value + "_not_null"
        val clazz = Poet.Pgen.notNullConstraint
        addProperty(
            name = name.toCamelCase(capitalized = false),
            type = clazz,
        ) {
            @Suppress("SpreadOperator")
            initializer(
                "%T(column = %L.%L, name = %S)",
                clazz,
                this@toConstraintsObject.name.prettyName,
                column.prettyName,
                name,
            )
        }
    }
}

context(c: CodeGenContext)
private fun Table.toTypeSpecEntity() = buildDataClass(this@toTypeSpecEntity.entityTypeName.simpleName) {
    addSuperinterface(Poet.Pgen.columnValueSet)
    primaryConstructor {
        this@toTypeSpecEntity.columns.forEach { column ->
            val type = column.getColumnTypeName()
            addParameter(column.prettyName, type)
            addProperty(name = column.prettyName, type = type) {
                initializer(column.prettyName)
            }
        }
    }

    addFunction("toList") {
        addModifiers(KModifier.OVERRIDE)
        returns(List::class.asTypeName().parameterizedBy(Poet.Pgen.columnValue.parameterizedBy(STAR)))
        addCode {
            add("return listOfNotNull(\n")
            this@toTypeSpecEntity.columns.forEach { column ->
                add(
                    "  %T(column = %T.%L, value = %L),\n",
                    Poet.Pgen.columnValue,
                    this@toTypeSpecEntity.name.typeName,
                    column.prettyName,
                    column.prettyName,
                )
            }
            add(")")
        }
    }

    addCompanionObject {
        addFunction("create") {
            addParameter(name = "row", type = Poet.Exposed.resultRow)
            addParameter(name = "alias", type = Poet.Exposed.alias.parameterizedBy(STAR).copy(nullable = true)) {
                this.defaultValue("null")
            }
            returns(this@toTypeSpecEntity.entityTypeName)
            addCode {
                add("return %T(\n", this@toTypeSpecEntity.entityTypeName)
                this@toTypeSpecEntity.columns.forEach { column ->
                    add(
                        "  %L = row.%T(%T.%L, alias),\n",
                        column.prettyName,
                        Poet.Pgen.getColumnWithAlias,
                        this@toTypeSpecEntity.name.typeName,
                        column.prettyName,
                    )
                }
                add(")")
            }
        }
    }
}

context(c: CodeGenContext)
private fun Table.toTypeSpecUpdateEntity(
) = buildDataClass(this@toTypeSpecUpdateEntity.updateEntityTypeName.simpleName) {
    addSuperinterface(Poet.Pgen.columnValueSet)
    primaryConstructor {
        this@toTypeSpecUpdateEntity.columns.forEach { column ->
            val innerType = column.getColumnTypeName()
            val type = Poet.QuatiUtil.option.parameterizedBy(innerType)
            addParameter(column.prettyName, type)
            addProperty(name = column.prettyName, type = type) {
                initializer(column.prettyName)
            }
        }
    }

    addFunction("toList") {
        addModifiers(KModifier.OVERRIDE)
        returns(List::class.asTypeName().parameterizedBy(Poet.Pgen.columnValue.parameterizedBy(STAR)))
        addCode {
            add("return listOfNotNull(\n")
            this@toTypeSpecUpdateEntity.columns.forEach { column ->
                add(
                    "  %L.%T()?.let { %T(column = %T.%L, value = it.value) },\n",
                    column.prettyName,
                    Poet.QuatiUtil.optionTakeSome,
                    Poet.Pgen.columnValue,
                    this@toTypeSpecUpdateEntity.name.typeName,
                    column.prettyName,
                )
            }
            add(")")
        }
    }
}


context(c: CodeGenContext)
private fun Table.toTypeSpecCreateEntity(
) = buildDataClass(this@toTypeSpecCreateEntity.createEntityTypeName.simpleName) {
    addSuperinterface(Poet.Pgen.columnValueSet)
    primaryConstructor {
        this@toTypeSpecCreateEntity.columns.forEach { column ->
            val innerType = column.getColumnTypeName()
            val type = if (column.default == null)
                innerType
            else
                Poet.QuatiUtil.option.parameterizedBy(innerType)
            addParameter(column.prettyName, type)
            addProperty(name = column.prettyName, type = type) {
                initializer(column.prettyName)
            }
        }
    }

    addFunction("toList") {
        addModifiers(KModifier.OVERRIDE)
        returns(List::class.asTypeName().parameterizedBy(Poet.Pgen.columnValue.parameterizedBy(STAR)))
        addCode {
            add("return listOfNotNull(\n")
            this@toTypeSpecCreateEntity.columns.forEach { column ->
                if (column.default == null)
                    add(
                        "  %T(column = %T.%L, value =%L),\n",
                        Poet.Pgen.columnValue,
                        this@toTypeSpecCreateEntity.name.typeName,
                        column.prettyName,
                        column.prettyName,
                    )
                else
                    add(
                        "  %L.%T()?.let { %T(column = %T.%L, value = it.value) },\n",
                        column.prettyName,
                        Poet.QuatiUtil.optionTakeSome,
                        Poet.Pgen.columnValue,
                        this@toTypeSpecCreateEntity.name.typeName,
                        column.prettyName,
                    )
            }
            add(")")
        }
    }
}