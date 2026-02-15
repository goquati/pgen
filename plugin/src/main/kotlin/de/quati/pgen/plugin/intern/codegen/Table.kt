package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asTypeName
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addCompanionObject
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addInitializerBlock
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildDataClass
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.kotlin.util.poet.dsl.getter
import de.quati.kotlin.util.poet.dsl.initializer
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.pgen.plugin.intern.model.spec.Table
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.kotlin.util.poet.toCamelCase

context(c: CodeGenContext)
internal fun Table.toTypeSpecInternal() = buildObject(this@toTypeSpecInternal.name.prettyName) {
    val foreignKeysSingle = this@toTypeSpecInternal.foreignKeys.map { it.toTyped() }
        .filterIsInstance<Table.ForeignKeyTyped.SingleKey>()
        .associateBy { it.reference.sourceColumn }
    superclass(Poet.Exposed.table)
    addSuperinterface(
        if (isEventTable)
            Poet.Pgen.pgenWalEventTable
        else
            Poet.Pgen.pgenTable
    )
    addSuperclassConstructorParameter("%S", this@toTypeSpecInternal.name.toString())
    addFunction(
        name = "getTableNameWithSchema",
    ) {
        returns(Poet.Pgen.Shared.tableNameWithSchema)
        addModifiers(KModifier.OVERRIDE)
        addCode(
            "return %T(schema = %S, table = %S)",
            Poet.Pgen.Shared.tableNameWithSchema,
            this@toTypeSpecInternal.name.schema.name,
            this@toTypeSpecInternal.name.name,
        )
    }
    if (isEventTable)
        addFunction(name = "walEventMapper") {
            addModifiers(KModifier.OVERRIDE)
            addParameter(
                name = "event",
                type = Poet.Pgen.Shared.walEventChange.parameterizedBy(Poet.jsonObject)
            )
            returns(this@toTypeSpecInternal.eventTypeName)
            addCode("return Event(metaData = event.metaData, payload = event.payload.map(EventEntity::parse))")
        }

    this@toTypeSpecInternal.columns.forEach { column ->
        addProperty(
            name = column.prettyName,
            type = Poet.Exposed.column.parameterizedBy(column.getColumnTypeName()),
        ) {
            initializer {
                add(initializerBlock(column))
                column.getDefaultExpression()?.also { add(it) }
                foreignKeysSingle[column.name]?.also { fKey ->
                    add(
                        ".references(ref = %T.${fKey.reference.targetColumn.pretty}, fkName = %S)",
                        fKey.targetTable.typeName, fKey.name,
                    )
                }
                if (column.nullable)
                    add(".nullable()")
            }
        }
    }
    if (this@toTypeSpecInternal.primaryKey != null) {
        val columnNames = this@toTypeSpecInternal.columns.map { it.prettyName }
        addProperty(name = "primaryKey".makeDifferent(columnNames, ""), type = Poet.Exposed.primaryKey) {
            addModifiers(KModifier.OVERRIDE)
            initializer(
                "PrimaryKey(%L, name = %S)",
                this@toTypeSpecInternal.primaryKey.columns.joinToString(", ") { it.pretty },
                this@toTypeSpecInternal.primaryKey.name,
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
    if (isEventTable) {
        addType(toTypeSpecEventEntity())
        addType(toTypeSpecEvent())
    }
}

context(c: CodeGenContext)
private fun Table.toConstraintsObject() = buildObject(this@toConstraintsObject.constraintsTypeName.simpleName) {
    val typeNameTable = this@toConstraintsObject.name.typeName

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
            "%T(table = %T, name = %S$additionalFormat)",
            clazz,
            typeNameTable,
            name,
            *additionalArgs,
        )
    }

    this@toConstraintsObject.primaryKey?.also { pkey ->
        addConstraint(name = pkey.name, clazz = Poet.Pgen.pKeyConstraint)
    }
    this@toConstraintsObject.foreignKeys.forEach { fkey ->
        addConstraint(name = fkey.name, clazz = Poet.Pgen.fKeyConstraint)
    }
    this@toConstraintsObject.uniqueConstraints.forEach { con ->
        addConstraint(name = con.name, clazz = Poet.Pgen.uniqueConstraint)
    }
    this@toConstraintsObject.checkConstraints.forEach { con ->
        addConstraint(name = con.name, clazz = Poet.Pgen.checkConstraint)
    }
    this@toConstraintsObject.columns.filter { !it.nullable }.forEach { column ->
        val name = column.name.value + "_not_null"
        val clazz = Poet.Pgen.notNullConstraint
        addProperty(
            name = name.toCamelCase(capitalized = false),
            type = clazz,
        ) {
            @Suppress("SpreadOperator")
            initializer(
                "%T(column = %T.%L, name = %S)",
                clazz,
                typeNameTable,
                column.prettyName,
                name,
            )
        }
    }
}

context(c: CodeGenContext)
private fun Table.toTypeSpecEntity() = buildDataClass(this@toTypeSpecEntity.entityTypeName.simpleName) {
    val columns = this@toTypeSpecEntity.columns
    val tableTypeName = this@toTypeSpecEntity.name.typeName
    val entityTypeName = this@toTypeSpecEntity.entityTypeName

    addSuperinterface(Poet.Pgen.columnValueSet)
    primaryConstructor {
        columns.forEach { column ->
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
            columns.forEach { column ->
                add(
                    "  %T(column = %T.%L, value = %L),\n",
                    Poet.Pgen.columnValue,
                    tableTypeName,
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
            returns(entityTypeName)
            addCode {
                add("return %T(\n", entityTypeName)
                columns.forEach { column ->
                    add(
                        "  %L = row.%T(%T.%L, alias),\n",
                        column.prettyName,
                        Poet.Pgen.getColumnWithAlias,
                        tableTypeName,
                        column.prettyName,
                    )
                }
                add(")")
            }
        }
    }
}

context(c: CodeGenContext)
private fun Table.toTypeSpecEvent() = buildDataClass(this@toTypeSpecEvent.eventTypeName.simpleName) {
    addSuperinterface(
        Poet.schemaUtilObjectEvent(this@toTypeSpecEvent.name.schema)
            .parameterizedBy(this@toTypeSpecEvent.eventEntityTypeName)
    )
    primaryConstructor {
        run {
            val name = "metaData"
            val type = Poet.Pgen.Shared.walEventMetaData
            addParameter(name, type)
            addProperty(name = name, type = type) {
                initializer(name)
                addModifiers(KModifier.OVERRIDE)
            }
        }
        run {
            val name = "payload"
            val type = Poet.Pgen.Shared.walEventChangePayload.parameterizedBy(this@toTypeSpecEvent.eventEntityTypeName)
            addParameter(name, type)
            addProperty(name = name, type = type) {
                initializer(name)
                addModifiers(KModifier.OVERRIDE)
            }
        }
    }
    run {
        val name = "table"
        val type = Poet.Pgen.Shared.tableNameWithSchema
        addProperty(name = name, type = type) {
            getter {
                addCode("return %T.getTableNameWithSchema()", this@toTypeSpecEvent.name.typeName)
            }
            addModifiers(KModifier.OVERRIDE)
        }
    }
}

context(c: CodeGenContext)
private fun Table.toTypeSpecEventEntity() = buildDataClass(this@toTypeSpecEventEntity.eventEntityTypeName.simpleName) {
    val columns = this@toTypeSpecEventEntity.eventColumns
    val tableTypeName = this@toTypeSpecEventEntity.name.typeName
    val entityTypeName = this@toTypeSpecEventEntity.eventEntityTypeName

    addSuperinterface(Poet.Pgen.columnValueSet)
    primaryConstructor {
        columns.forEach { column ->
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
            columns.forEach { column ->
                add(
                    "  %T(column = %T.%L, value = %L),\n",
                    Poet.Pgen.columnValue,
                    tableTypeName,
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
            returns(entityTypeName)
            addCode {
                add("return %T(\n", entityTypeName)
                columns.forEach { column ->
                    add(
                        "  %L = row.%T(%T.%L, alias),\n",
                        column.prettyName,
                        Poet.Pgen.getColumnWithAlias,
                        tableTypeName,
                        column.prettyName,
                    )
                }
                add(")")
            }
        }

        addFunction("parse") {
            addParameter(name = "data", type = Poet.jsonObject)
            returns(entityTypeName)
            addCode {
                add("return %T(\n", entityTypeName)
                columns.forEach { column ->
                    add(
                        "  %L = %T(data, %T.%L),\n",
                        column.prettyName,
                        if (column.nullable) Poet.Pgen.parseColumnNullable else Poet.Pgen.parseColumn,
                        tableTypeName,
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
            val type = if (column.defaultExpr == null)
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
                if (column.defaultExpr == null)
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