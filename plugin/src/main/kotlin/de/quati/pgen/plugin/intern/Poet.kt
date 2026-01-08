package de.quati.pgen.plugin.intern

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal fun fileSpec(
    packageName: PackageName,
    name: String,
    block: FileSpec.Builder.() -> Unit,
) = FileSpec.builder(packageName = packageName.name, fileName = name).apply(block).build()

internal fun buildObject(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.objectBuilder(name).apply(block).build()

internal fun FileSpec.Builder.addObject(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.objectBuilder(name).apply(block).build())

internal fun buildEnum(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.enumBuilder(name).apply(block).build()

internal fun buildInterface(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.interfaceBuilder(name).apply(block).build()

internal fun FileSpec.Builder.addInterface(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.interfaceBuilder(name).apply(block).build())

internal fun buildClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply(block).build()

internal fun buildDataClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply { addModifiers(KModifier.DATA) }.apply(block).build()

internal fun buildValueClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply {
    addModifiers(KModifier.VALUE)
    addAnnotation(JvmInline::class)
    block()
}.build()

public fun PropertySpec.Builder.initializer(
    block: CodeBlock.Builder.() -> Unit,
): PropertySpec.Builder = initializer(CodeBlock.builder().apply(block).build())

internal fun TypeSpec.Builder.addClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(buildClass(name = name, block = block))

internal fun FileSpec.Builder.addClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(buildClass(name = name, block = block))

internal fun FileSpec.Builder.addProperty(name: String, type: TypeName, block: PropertySpec.Builder.() -> Unit) =
    addProperty(PropertySpec.builder(name = name, type = type).apply(block).build())

internal fun TypeSpec.Builder.addProperty(name: String, type: TypeName, block: PropertySpec.Builder.() -> Unit) =
    addProperty(PropertySpec.builder(name = name, type = type).apply(block).build())

internal fun PropertySpec.Builder.getter(block: FunSpec.Builder.() -> Unit) =
    getter(FunSpec.getterBuilder().apply(block).build())

internal fun TypeSpec.Builder.addEnumConstant(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addEnumConstant(name, TypeSpec.anonymousClassBuilder().apply(block).build())

internal fun TypeSpec.Builder.primaryConstructor(
    block: FunSpec.Builder.() -> Unit,
) = primaryConstructor(FunSpec.constructorBuilder().apply(block).build())

internal fun TypeSpec.Builder.addCompanionObject(
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.companionObjectBuilder().apply(block).build())

internal fun TypeSpec.Builder.addObject(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.objectBuilder(name).apply(block).build())

internal fun buildFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = FunSpec.builder(name).apply(block).build()

internal fun TypeSpec.Builder.addFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = addFunction(buildFunction(name = name, block = block))

internal fun FileSpec.Builder.addFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = addFunction(buildFunction(name = name, block = block))

internal fun TypeSpec.Builder.addInitializerBlock(block: CodeBlock.Builder.() -> Unit) =
    addInitializerBlock(CodeBlock.builder().apply(block).build())

internal fun FunSpec.Builder.addParameter(
    name: String,
    type: TypeName,
    block: ParameterSpec.Builder.() -> Unit,
) = addParameter(ParameterSpec.builder(name, type).apply(block).build())

internal fun FunSpec.Builder.addCode(
    block: CodeBlock.Builder.() -> Unit
) = addCode(CodeBlock.builder().apply(block).build())

internal fun CodeBlock.Builder.addControlFlow(
    controlFlow: String,
    vararg args: Any,
    block: CodeBlock.Builder.() -> Unit
) {
    beginControlFlow(controlFlow, *args)
    block()
    endControlFlow()
}

internal fun CodeBlock.Builder.indent(block: CodeBlock.Builder.() -> Unit) {
    indent()
    block()
    unindent()
}

@JvmInline
internal value class PackageName(val name: String) {
    override fun toString(): String = name
    operator fun plus(subPackage: String) = PackageName("$name.$subPackage")
    internal fun className(vararg name: String) = ClassName(this.name, *name)
}
