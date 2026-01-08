package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.intern.addCode
import de.quati.pgen.plugin.intern.addCompanionObject
import de.quati.pgen.plugin.intern.addFunction
import de.quati.pgen.plugin.intern.addInitializerBlock
import de.quati.pgen.plugin.intern.addParameter
import de.quati.pgen.plugin.intern.addProperty
import de.quati.pgen.plugin.intern.buildDataClass
import de.quati.pgen.plugin.intern.buildObject
import de.quati.pgen.plugin.intern.getter
import de.quati.pgen.plugin.intern.primaryConstructor
import de.quati.pgen.plugin.intern.model.sql.Table
import de.quati.pgen.plugin.intern.util.makeDifferent
import de.quati.pgen.plugin.intern.util.toCamelCase

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
    addSuperclassConstructorParameter(
        "%S",
        "${this@toTypeSpecInternal.name.schema.schemaName}.${this@toTypeSpecInternal.name.name}"
    )
    addFunction(
        name = "getTableNameWithSchema",
    ) {
        returns(Poet.Pgen.Shared.tableNameWithSchema)
        addModifiers(KModifier.OVERRIDE)
        addCode(
            "return %T(schema = %S, table = %S)",
            Poet.Pgen.Shared.tableNameWithSchema,
            this@toTypeSpecInternal.name.schema.schemaName,
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
    if (isEventTable) {
        addType(toTypeSpecEventEntity())
        addType(toTypeSpecEvent())
    }
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
                        if (column.isNullable) Poet.Pgen.parseColumnNullable else Poet.Pgen.parseColumn,
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