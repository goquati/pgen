package de.quati.pgen.plugin.intern.util.codegen

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.intern.addCode
import de.quati.pgen.plugin.intern.addCompanionObject
import de.quati.pgen.plugin.intern.addEnumConstant
import de.quati.pgen.plugin.intern.addFunction
import de.quati.pgen.plugin.intern.addProperty
import de.quati.pgen.plugin.intern.buildEnum
import de.quati.pgen.plugin.intern.primaryConstructor
import de.quati.pgen.plugin.intern.model.sql.Enum
import de.quati.pgen.plugin.intern.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.intern.util.toSnakeCase

private fun String.toEnumName() = this.toSnakeCase(uppercase = true)

private fun KotlinEnumClass.getMappingPair(field: String): Pair<String, String> {
    val enumName = field.toEnumName()
    val otherName = mappings[enumName] ?: enumName
    return enumName to otherName
}

context(c: CodeGenContext)
internal fun Enum.toTypeSpecInternal() = buildEnum(this@toTypeSpecInternal.name.prettyName) {
    addSuperinterface(Poet.Pgen.Shared.pgenEnum)
    primaryConstructor {
        addParameter("pgenEnumLabel", String::class)
        addProperty(name = "pgenEnumLabel", type = String::class.asTypeName()) {
            addModifiers(KModifier.OVERRIDE)
            initializer("pgenEnumLabel")
        }
    }
    this@toTypeSpecInternal.fields.forEach { field ->
        val enumName = field.toEnumName()
        addEnumConstant(enumName) {
            addSuperclassConstructorParameter("pgenEnumLabel = %S", field)
        }
    }
    val pgEnumTypeNameValue = "${this@toTypeSpecInternal.name.schema.schemaName}.${this@toTypeSpecInternal.name.name}"
    addProperty(name = "pgenEnumTypeName", type = String::class.asTypeName()) {
        initializer("%S", pgEnumTypeNameValue)
        addModifiers(KModifier.OVERRIDE)
    }

    val enumMapping = c.enumMappings[name]
    if (enumMapping != null)
        addFunction("toDto") {
            returns(enumMapping.name.poet)
            addCode {
                beginControlFlow("return when (this)")
                this@toTypeSpecInternal.fields.forEach { field ->
                    val (enumName, otherName) = enumMapping.getMappingPair(field)
                    add(
                        "%T.%L -> %T.%L\n",
                        this@toTypeSpecInternal.name.typeName,
                        enumName,
                        enumMapping.name.poet,
                        otherName,
                    )
                }
                endControlFlow()
            }
        }

    if (enumMapping != null)
        addCompanionObject {
            addFunction("toEntity") {
                receiver(enumMapping.name.poet)
                returns(this@toTypeSpecInternal.name.typeName)
                addCode {
                    beginControlFlow("return when (this)")
                    this@toTypeSpecInternal.fields.forEach { field ->
                        val (enumName, otherName) = enumMapping.getMappingPair(field)
                        add(
                            "%T.%L -> %T.%L\n",
                            enumMapping.name.poet,
                            otherName,
                            this@toTypeSpecInternal.name.typeName,
                            enumName,
                        )
                    }
                    endControlFlow()
                }
            }
        }
}
