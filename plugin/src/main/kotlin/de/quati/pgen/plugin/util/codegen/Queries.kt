package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import de.quati.pgen.plugin.dsl.addParameter
import de.quati.pgen.plugin.dsl.buildFunction
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.sql.Table

context(c: CodeGenContext)
internal fun Table.toQueryFunctions() = listOfNotNull(
    toDeleteByIdOrThrow(),
)

context(c: CodeGenContext)
internal fun Table.toDeleteByIdOrThrow(): FunSpec? {
    val pk = primaryKey?.columnNames?.singleOrNull()?.let { pkName ->
        columns.singleOrNull { it.name == pkName }
    } ?: return null
    return buildFunction(name = "deleteByIdOrThrow") {
        when (c.connectionType) {
            Config.ConnectionType.JDBC -> Unit
            Config.ConnectionType.R2DBC -> addModifiers(KModifier.SUSPEND)
        }
        addParameter(name = "id", type = pk.type.getTypeName())
        receiver(this@toDeleteByIdOrThrow.name.typeName)
        addParameter(
            name = "andWhere",
            type = LambdaTypeName.get(
                receiver = this@toDeleteByIdOrThrow.name.typeName,
                returnType = Poet.Exposed.opBoolean,
            ).copy(nullable = true),
        ) { defaultValue("null") }
        returns(Unit::class)

        addCode(
            "val op = (%T.%L %T id).let { if (andWhere == null) it else (it %T andWhere()) }\n",
            this@toDeleteByIdOrThrow.name.typeName,
            pk.prettyName,
            Poet.Exposed.eq,
            Poet.Exposed.and,

            )
        addCode("val count = %T { op }\n", Poet.Exposed.deleteWhere())
        beginControlFlow("return when (count)")
        addCode("0 -> throw %T.NotFound(\"no ${'$'}tableName by id '${'$'}id' found\")\n", Poet.QuatiUtil.exception)
        addCode("1 -> Unit\n")
        addCode("else -> error(\"multiple ${'$'}tableName by id '${'$'}id' found\")\n")
        endControlFlow()
    }
}