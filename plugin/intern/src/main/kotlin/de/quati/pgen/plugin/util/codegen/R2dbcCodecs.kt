package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.pgen.plugin.dsl.addProperty
import de.quati.pgen.plugin.model.sql.Enum

context(c: CodeGenContext) fun FileSpec.Builder.createCodecCollection(
    objs: Collection<Enum>,
) {
    addProperty(
        name = "pgenCodec",
        type = Poet.R2dbc.codecRegistrar
    ) {
        initializer(buildCodeBlock {
            add("%T.builder()\n", Poet.R2dbc.enumCodec)
            objs.forEach { add("  .withEnum(%S, %T::class.java)\n", it.name.name.lowercase(), it.name.typeName) }
            add("  .build()\n")
        })
    }
}