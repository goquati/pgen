package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.intern.addFunction
import de.quati.pgen.plugin.intern.addProperty
import de.quati.pgen.plugin.intern.buildValueClass
import de.quati.pgen.plugin.intern.primaryConstructor
import de.quati.pgen.plugin.intern.model.sql.Column.Type.NonPrimitive.Domain

context(c: CodeGenContext)
internal fun Domain.toTypeSpecInternal() = buildValueClass(this@toTypeSpecInternal.name.prettyName) {
    val dataFieldName = "value"

    val typename = originalType.getTypeName()
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
