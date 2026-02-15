package de.quati.pgen.plugin.intern.codegen

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asTypeName
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildValueClass
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.pgen.plugin.intern.model.spec.Column.Type.NonPrimitive.Domain

context(c: CodeGenContext)
internal fun Domain.toTypeSpecInternal() = buildValueClass(this@toTypeSpecInternal.ref.prettyName) {
    val dataFieldName = "value"

    val typename = base.getTypeName()
    val isStringLike = typename == String::class.asTypeName()
    if (isStringLike)
        addSuperinterface(Poet.Pgen.Shared.stringLike)
    primaryConstructor {
        addParameter(dataFieldName, typename)
        addProperty(name = dataFieldName, type = typename) {
            initializer(dataFieldName)
            if (isStringLike)
                addModifiers(KModifier.OVERRIDE)
        }
    }
    addFunction(name = "toString") {
        addModifiers(KModifier.OVERRIDE)
        returns(String::class)
        addCode("return $dataFieldName.toString()")
    }
}
