package de.quati.pgen.plugin.util.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.asTypeName
import de.quati.pgen.plugin.dsl.addCode
import de.quati.pgen.plugin.dsl.addCompanionObject
import de.quati.pgen.plugin.dsl.addEnumConstant
import de.quati.pgen.plugin.dsl.addFunction
import de.quati.pgen.plugin.dsl.addProperty
import de.quati.pgen.plugin.dsl.buildEnum
import de.quati.pgen.plugin.dsl.primaryConstructor
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.sql.Enum
import de.quati.pgen.plugin.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.util.toSnakeCase

context(c: CodeGenContext)
private fun String.toEnumName() = when (c.connectionType) {
    Config.ConnectionType.JDBC -> this.toSnakeCase(uppercase = true)
    Config.ConnectionType.R2DBC -> this
}

context(c: CodeGenContext)
private fun KotlinEnumClass.getMappingPair(field: String): Pair<String, String> {
    val enumName = field.toEnumName()
    val otherName = mappings[enumName] ?: enumName
    return enumName to otherName
}

context(c: CodeGenContext)
internal fun Enum.toTypeSpecInternal() = buildEnum(this@toTypeSpecInternal.name.prettyName) {
    addSuperinterface(Poet.Pgen.pgEnum)
    primaryConstructor {
        addParameter("pgEnumLabel", String::class)
        addProperty(name = "pgEnumLabel", type = String::class.asTypeName()) {
            addModifiers(KModifier.OVERRIDE)
            initializer("pgEnumLabel")
        }
    }
    this@toTypeSpecInternal.fields.forEach { field ->
        val enumName = field.toEnumName()
        addEnumConstant(enumName) {
            addSuperclassConstructorParameter("pgEnumLabel = %S", field)
        }
    }
    val pgEnumTypeNameValue = "${this@toTypeSpecInternal.name.schema.schemaName}.${this@toTypeSpecInternal.name.name}"
    addProperty(name = "pgEnumTypeName", type = String::class.asTypeName()) {
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

    if (c.connectionType == Config.ConnectionType.R2DBC)
        addCompanionObject {
            addProperty(name = "codec", type = Poet.codecRegistrar) {
                initializer(
                    "%T.builder().withEnum(%S.lowercase(), " +
                            "${this@toTypeSpecInternal.name.prettyName}::class.java).build()",
                    ClassName("io.r2dbc.postgresql.codec", "EnumCodec"),
                    this@toTypeSpecInternal.name.name.lowercase(),
                )
            }

            if (enumMapping != null)
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
