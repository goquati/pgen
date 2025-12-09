package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.dsl.addProperty
import de.quati.pgen.plugin.model.sql.Enum


context(c: CodeGenContext) fun FileSpec.Builder.createCodecCollection(
    objs: Collection<Enum>,
) {
    this.addProperty(
        name = "allR2dbcCodecs",
        type = List::class.asTypeName().parameterizedBy(Poet.codecRegistrar)
    ) {
        @Suppress("SpreadOperator") initializer(
            objs.joinToString(prefix = "listOf(", postfix = ")", separator = ", ") { "%T.codec" },
            *(objs.map { it.name.typeName }.toTypedArray()),
        )
    }
}