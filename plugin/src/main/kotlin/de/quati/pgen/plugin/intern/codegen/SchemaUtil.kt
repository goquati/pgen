package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.addObject
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildInterface
import de.quati.kotlin.util.poet.dsl.getter
import de.quati.pgen.plugin.intern.model.spec.SchemaName
import de.quati.pgen.plugin.intern.model.spec.SqlObject
import de.quati.pgen.plugin.intern.model.spec.Table


context(c: CodeGenContext)
internal fun FileSpec.Builder.addSchemaUtils(
    schema: SchemaName,
    allObjects: Collection<SqlObject>,
) {
    addObject(name = Poet.schemaUtilObject(schema).simpleName) {
        addType(
            buildInterface(
                name = "Event"
            ) {
                val tType = TypeVariableName("T", ANY)
                addModifiers(KModifier.SEALED)
                addTypeVariable(tType)
                addSuperinterface(
                    Poet.Pgen.Shared.walEventChange.parameterizedBy(tType)
                )
            }
        )

        addProperty(
            name = "tables",
            type = List::class.asTypeName().parameterizedBy(Poet.Pgen.Core.pgenTable),
        ) {
            initializer(buildCodeBlock {
                add("listOf(\n")
                allObjects.filterIsInstance<Table>().forEach { table ->
                    add("    %T,\n", table.name.typeName)
                }
                add(")")
            })
        }

        addProperty(
            name = "eventTables",
            type = List::class.asTypeName().parameterizedBy(Poet.Pgen.Core.pgenWalEventTable),
        ) {
            getter {
                addStatement(
                    "return tables.filterIsInstance<%T>()",
                    Poet.Pgen.Core.pgenWalEventTable,
                )
            }
        }
    }
}
