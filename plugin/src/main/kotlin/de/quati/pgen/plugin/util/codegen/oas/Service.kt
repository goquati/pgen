package de.quati.pgen.plugin.util.codegen.oas

import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.UNIT
import de.quati.pgen.plugin.dsl.addCode
import de.quati.pgen.plugin.dsl.addFunction
import de.quati.pgen.plugin.dsl.addInterface
import de.quati.pgen.plugin.dsl.addParameter
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.oas.TableOasData
import de.quati.pgen.plugin.util.codegen.CodeGenContext
import de.quati.pgen.plugin.util.codegen.Poet
import de.quati.pgen.plugin.util.codegen.getColumnTypeName

@OptIn(ExperimentalKotlinPoetApi::class)
context(c: CodeGenContext, mapperConfig: Config.OasConfig.Mapper)
fun FileSpec.Builder.addTableService(data: TableOasData) = addInterface(data.getOasServiceName()) {

    val idType = data.sqlData.columns.singleOrNull { it.prettyName == "id" }
        // TODO check if id is primary key
        ?.getColumnTypeName()
        ?: error("no id column found")

    addProperty("db", Poet.Exposed.database())

    addFunction("getById") {
        val localConfigContext = c.localConfigContext?.takeIf { Config.OasConfig.CRUD.READ in it.atMethods }
        localConfigContext?.also { contextParameter("c", it.type) }
        addModifiers(KModifier.SUSPEND)
        addParameter("id", idType)
        returns(data.sqlData.entityTypeName)
        addCode(
            """
            return getAll { %T.id eq id }
                .%T()
                ?: throw %T.NotFound("${data.namePretty} not found with id: ${'$'}id")
            """.trimIndent(),
            data.sqlData.name.typeName,
            Poet.flowSingleOrNull,
            Poet.QuatiUtil.exception,
        )
    }

    addFunction("getAll") {
        val localConfigContext = c.localConfigContext?.takeIf { Config.OasConfig.CRUD.READ in it.atMethods }
        localConfigContext?.also { contextParameter("c", it.type) }
        addModifiers(KModifier.SUSPEND)
        addParameter(
            "fieldSetMapper",
            LambdaTypeName.get(null, listOf(ParameterSpec("fieldSet", Poet.Exposed.fieldSet)), Poet.Exposed.fieldSet)
                .copy(nullable = true),
        ) {
            defaultValue("null")
        }
        addParameter(
            "queryMapper",
            LambdaTypeName.get(null, listOf(ParameterSpec("query", Poet.Exposed.query())), Poet.Exposed.query())
                .copy(nullable = true),
        ) {
            defaultValue("null")
        }
        addParameter(
            "filter",
            LambdaTypeName.get(parameters = emptyList(), returnType = Poet.Exposed.opBoolean).copy(nullable = true),
        ) {
            defaultValue("null")
        }
        returns(Poet.flow.parameterizedBy(data.sqlData.entityTypeName))
        addCode {
            beginControlFlow("return %T", Poet.channelFlow)
            beginControlFlow("%T(db = db, readOnly = true)", Poet.Exposed.transactionFun())
            add(
                """
                ${"// ".takeIf { localConfigContext == null } ?: ""}%T(c)
                %T
                    .let { if (fieldSetMapper == null) it else fieldSetMapper(it) }
                    .%T()
                    .let { if (filter == null) it else it.where(filter) }
                    .let { if (queryMapper == null) it else queryMapper(it) }
                    .%T(%T.Entity::create)
                    .collect { send(it) }
            """.trimIndent(),
                Poet.Pgen.setLocalConfig,
                data.sqlData.name.typeName,
                Poet.Exposed.selectAll(),
                Poet.flowMap,
                data.sqlData.name.typeName,
            )
            endControlFlow()
            endControlFlow()
        }
    }

    addFunction("create") {
        val localConfigContext = c.localConfigContext?.takeIf { Config.OasConfig.CRUD.CREATE in it.atMethods }
        localConfigContext?.also { contextParameter("c", it.type) }
        addModifiers(KModifier.SUSPEND)
        addParameter("data", data.sqlData.updateEntityTypeName)
        returns(data.sqlData.entityTypeName)
        addCode {
            beginControlFlow("return %T(db = db)", Poet.Exposed.transactionFun())
            add(
                """
                ${"// ".takeIf { localConfigContext == null } ?: ""}%T(c)
                %T.%T(ignoreErrors = true) {
                    data applyTo it
                }.%T()
                    .let { it ?: throw %T.BadRequest("Cannot create ${data.namePretty}") }
                    .let(%T.Entity::create)
            """.trimIndent(),
                Poet.Pgen.setLocalConfig,
                data.sqlData.name.typeName,
                Poet.Exposed.insertReturning(),
                Poet.flowSingleOrNull,
                Poet.QuatiUtil.exception,
                data.sqlData.name.typeName,
            )
            endControlFlow()
        }
    }

    addFunction("delete") {
        val localConfigContext = c.localConfigContext?.takeIf { Config.OasConfig.CRUD.DELETE in it.atMethods }
        localConfigContext?.also { contextParameter("c", it.type) }
        addModifiers(KModifier.SUSPEND)
        addParameter("id", idType)
        returns(UNIT)
        addCode {
            beginControlFlow("return %T(db = db)", Poet.Exposed.transactionFun())
            add(
                """
                ${"// ".takeIf { localConfigContext == null } ?: ""}%T(c)
                %T.%T { %T.id %T id }
                """.trimIndent(),
                Poet.Pgen.setLocalConfig,
                data.sqlData.name.typeName,
                Poet.Exposed.deleteWhere(),
                data.sqlData.name.typeName,
                Poet.Exposed.eq
            )
            endControlFlow()
            beginControlFlow(".let")
            add(
                """if (it == 0) throw %T.NotFound("Cannot delete ${data.namePretty} with id: ${'$'}id")${'\n'}""",
                Poet.QuatiUtil.exception,
            )
            add("Unit\n")
            endControlFlow()
        }
    }

    addFunction("update") {
        val localConfigContext = c.localConfigContext?.takeIf { Config.OasConfig.CRUD.UPDATE in it.atMethods }
        localConfigContext?.also { contextParameter("c", it.type) }
        addModifiers(KModifier.SUSPEND)
        addParameter("id", idType)
        addParameter("data", data.sqlData.updateEntityTypeName)
        returns(data.sqlData.entityTypeName)
        addCode {
            beginControlFlow("return %T(db = db)", Poet.Exposed.transactionFun())
            add(
                """
                ${"// ".takeIf { localConfigContext == null } ?: ""}%T(c)
                %T.%T(where = { %T.id eq id }) {
                    data applyTo it
                }.%T()
                    .let { it ?: throw %T.BadRequest("Cannot update ${data.namePretty} with id: ${'$'}id") }
                    .let(%T.Entity::create)
                """.trimIndent(),
                Poet.Pgen.setLocalConfig,
                data.sqlData.name.typeName,
                Poet.Exposed.updateReturning(),
                data.sqlData.name.typeName,
                Poet.flowSingleOrNull,
                Poet.QuatiUtil.exception,
                data.sqlData.name.typeName,
            )
            endControlFlow()
        }
    }
}